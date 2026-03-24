package dev.tvtuner.feature.livetv

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tvtuner.core.data.db.entity.ChannelEntity
import dev.tvtuner.tuner.core.TunerBackend
import dev.tvtuner.tuner.core.TunerBackendType
import dev.tvtuner.tuner.core.TunerManager
import dev.tvtuner.tuner.core.TuneRequest
import dev.tvtuner.tuner.core.TunerResult
import dev.tvtuner.tuner.network.HdhrTunerBackend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the live TV playback pipeline.
 *
 * For HDHomeRun devices: tunes the device and passes the HTTP stream URL to ExoPlayer.
 * For USB devices: TODO — requires transport stream injection into ExoPlayer pipeline.
 * For Fake backend: plays a static test stream URL (or blank video).
 */
@Singleton
class PlaybackEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tunerManager: TunerManager,
) {
    companion object {
        private const val TAG = "PlaybackEngine"
        // Placeholder test stream for fake/demo mode only
        private const val FAKE_STREAM_URL = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
    }

    private var exoPlayer: ExoPlayer? = null

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    fun getOrCreatePlayer(): ExoPlayer {
        return exoPlayer ?: ExoPlayer.Builder(context).build().also {
            exoPlayer = it
            it.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    _playbackState.value = when (state) {
                        Player.STATE_BUFFERING -> PlaybackState.Buffering
                        Player.STATE_READY -> PlaybackState.Playing
                        Player.STATE_ENDED -> PlaybackState.Ended
                        Player.STATE_IDLE -> PlaybackState.Idle
                        else -> PlaybackState.Idle
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Log.e(TAG, "ExoPlayer error: ${error.message}", error)
                    _playbackState.value = PlaybackState.Error(error.message ?: "Unknown error")
                }
            })
        }
    }

    suspend fun tuneAndPlay(channel: ChannelEntity) {
        val activeBackend = tunerManager.getActiveBackend()
        val streamUrl = when {
            activeBackend is HdhrTunerBackend -> {
                val result = tunerManager.tune(
                    TuneRequest(rfChannelKhz = channel.rfChannelKhz, programNumber = channel.programNumber)
                )
                if (result is TunerResult.Failure) {
                    _playbackState.value = PlaybackState.Error(result.error.message)
                    return
                }
                activeBackend.getStreamUrl() ?: return
            }
            activeBackend?.backendName == TunerBackendType.FAKE.name -> {
                Log.w(TAG, "*** FAKE STREAM — not real broadcast ***")
                FAKE_STREAM_URL
            }
            else -> {
                // USB backend: stream injection into ExoPlayer not yet implemented
                // See DEVELOPMENT_STATUS.md §Playback Pipeline
                Log.w(TAG, "USB transport stream injection not yet implemented")
                _playbackState.value = PlaybackState.Error("USB playback not yet implemented — see DEVELOPMENT_STATUS.md")
                return
            }
        }

        val player = getOrCreatePlayer()
        val mediaItem = MediaItem.fromUri(streamUrl)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
        Log.i(TAG, "Playing: ${channel.displayName} from $streamUrl")
    }

    fun stop() {
        exoPlayer?.stop()
        _playbackState.value = PlaybackState.Idle
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
        _playbackState.value = PlaybackState.Idle
    }
}

sealed class PlaybackState {
    data object Idle : PlaybackState()
    data object Buffering : PlaybackState()
    data object Playing : PlaybackState()
    data object Ended : PlaybackState()
    data class Error(val message: String) : PlaybackState()
}
