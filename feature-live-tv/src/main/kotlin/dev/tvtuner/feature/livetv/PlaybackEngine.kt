package dev.tvtuner.feature.livetv

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tvtuner.core.data.db.entity.ChannelEntity
import dev.tvtuner.tuner.core.TunerBackendType
import dev.tvtuner.tuner.core.TunerManager
import dev.tvtuner.tuner.core.TuneRequest
import dev.tvtuner.tuner.core.TunerResult
import dev.tvtuner.tuner.network.HdhrTunerBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages live TV playback.
 *
 * Routing:
 *   • HDHomeRun network tuner  → tunes device, hands HTTP stream URL to ExoPlayer directly.
 *   • MyGica USB tuner (USB)   → reads raw MPEG-2 TS from USB, pipes it into ExoPlayer
 *                                 via a [TsLiveDataSource] + [ProgressiveMediaSource].
 *   • Fake backend             → plays a static HLS test stream (demo/testing only).
 */
@Singleton
class PlaybackEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tunerManager: TunerManager,
) {
    companion object {
        private const val TAG = "PlaybackEngine"
        private const val FAKE_STREAM_URL = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
        /** Capacity of the TS channel buffer; limits memory if ExoPlayer stalls. */
        private const val TS_CHANNEL_CAPACITY = 64
    }

    private var exoPlayer: ExoPlayer? = null

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    /** Scope + job for the USB TS pipe coroutine (non-null while USB stream active). */
    private var usbScope: CoroutineScope? = null
    private var tsChannel: Channel<ByteArray>? = null

    fun getOrCreatePlayer(): ExoPlayer {
        return exoPlayer ?: ExoPlayer.Builder(context).build().also {
            exoPlayer = it
            it.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    _playbackState.value = when (state) {
                        Player.STATE_BUFFERING -> PlaybackState.Buffering
                        Player.STATE_READY     -> PlaybackState.Playing
                        Player.STATE_ENDED     -> PlaybackState.Ended
                        else                   -> PlaybackState.Idle
                    }
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Log.e(TAG, "ExoPlayer error: ${error.message}", error)
                    _playbackState.value = PlaybackState.Error(error.message ?: "Playback error")
                }
            })
        }
    }

    suspend fun tuneAndPlay(channel: ChannelEntity) {
        // Stop any prior USB stream pipe before switching channels
        stopUsbPipe()

        val activeBackend = tunerManager.getActiveBackend()

        when {
            // ── HDHomeRun network tuner ────────────────────────────────────────
            activeBackend is HdhrTunerBackend -> {
                val result = tunerManager.tune(
                    TuneRequest(rfChannelKhz = channel.rfChannelKhz,
                                programNumber = channel.programNumber))
                if (result is TunerResult.Failure) {
                    _playbackState.value = PlaybackState.Error(result.error.message)
                    return
                }
                val streamUrl = activeBackend.getStreamUrl()
                if (streamUrl == null) {
                    _playbackState.value = PlaybackState.Error("HDHomeRun: no stream URL")
                    return
                }
                playUrl(streamUrl)
            }

            // ── MyGica USB tuner ───────────────────────────────────────────────
            activeBackend?.backendName == TunerBackendType.USB_MYGICA.name -> {
                val result = tunerManager.tune(
                    TuneRequest(rfChannelKhz = channel.rfChannelKhz,
                                programNumber = channel.programNumber))
                if (result is TunerResult.Failure) {
                    _playbackState.value = PlaybackState.Error(result.error.message)
                    return
                }
                playUsbTransportStream()
            }

            // ── Fake / Demo backend ────────────────────────────────────────────
            activeBackend?.backendName == TunerBackendType.FAKE.name -> {
                Log.w(TAG, "*** FAKE STREAM — not real broadcast ***")
                playUrl(FAKE_STREAM_URL)
            }

            else -> {
                _playbackState.value = PlaybackState.Error(
                    "No active tuner. Select a tuner in Settings before watching.")
            }
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private fun playUrl(url: String) {
        val player = getOrCreatePlayer()
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
        Log.i(TAG, "Playing URL: $url")
    }

    /**
     * Wire the USB TS read flow into ExoPlayer via [TsLiveDataSource].
     *
     * A [Channel] acts as an in-process pipe:
     *   USB bulk read coroutine → [Channel] → [TsLiveDataSource].read() → ExoPlayer
     *
     * ExoPlayer's [ProgressiveMediaSource] handles MPEG-2 TS demux natively and
     * will select the program matching [channel.programNumber] via the PMT.
     */
    private fun playUsbTransportStream() {
        val channel = Channel<ByteArray>(capacity = TS_CHANNEL_CAPACITY)
        tsChannel = channel

        // Coroutine that feeds the channel from the USB read flow
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        usbScope = scope
        scope.launch {
            try {
                tunerManager.readTransportStream().collect { packet ->
                    channel.trySend(packet)   // drop if buffer full (live TV — no stale data)
                }
            } finally {
                channel.close()
            }
        }

        // Custom DataSource.Factory that creates one TsLiveDataSource per ExoPlayer open
        val factory = DataSource.Factory { TsLiveDataSource(channel) }
        val mediaSource = ProgressiveMediaSource.Factory(factory)
            .createMediaSource(MediaItem.fromUri("ts://live"))

        val player = getOrCreatePlayer()
        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = true
        Log.i(TAG, "Playing USB TS stream via ProgressiveMediaSource")
    }

    private fun stopUsbPipe() {
        tsChannel?.close()
        tsChannel = null
        usbScope?.cancel()
        usbScope = null
    }

    fun stop() {
        stopUsbPipe()
        exoPlayer?.stop()
        _playbackState.value = PlaybackState.Idle
    }

    fun release() {
        stopUsbPipe()
        exoPlayer?.release()
        exoPlayer = null
        _playbackState.value = PlaybackState.Idle
    }
}

sealed class PlaybackState {
    data object Idle      : PlaybackState()
    data object Buffering : PlaybackState()
    data object Playing   : PlaybackState()
    data object Ended     : PlaybackState()
    data class  Error(val message: String) : PlaybackState()
}
