package dev.tvtuner.feature.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tvtuner.core.data.db.entity.ChannelEntity
import dev.tvtuner.core.data.preferences.AppPreferences
import dev.tvtuner.core.data.repository.ChannelRepository
import dev.tvtuner.core.data.repository.GuideRepository
import dev.tvtuner.tuner.core.SignalMetrics
import dev.tvtuner.tuner.core.TunerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val guideRepository: GuideRepository,
    private val tunerManager: TunerManager,
    private val playbackEngine: PlaybackEngine,
    private val prefs: AppPreferences,
) : ViewModel() {

    val channels: StateFlow<List<ChannelEntity>> = channelRepository
        .observeVisibleChannels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playbackState: StateFlow<PlaybackState> = playbackEngine.playbackState

    private val _currentChannel = MutableStateFlow<ChannelEntity?>(null)
    val currentChannel: StateFlow<ChannelEntity?> = _currentChannel.asStateFlow()

    private val _overlayVisible = MutableStateFlow(true)
    val overlayVisible: StateFlow<Boolean> = _overlayVisible.asStateFlow()

    private val _signalMetrics = MutableStateFlow<SignalMetrics?>(null)
    val signalMetrics: StateFlow<SignalMetrics?> = _signalMetrics.asStateFlow()

    fun getPlayer() = playbackEngine.getOrCreatePlayer()

    fun tuneToChannel(channel: ChannelEntity) {
        viewModelScope.launch {
            _currentChannel.value = channel
            prefs.setLastWatchedChannelId(channel.id)
            playbackEngine.tuneAndPlay(channel)
            refreshSignalMetrics()
        }
    }

    fun channelUp() {
        val list = channels.value
        val current = _currentChannel.value ?: return
        val idx = list.indexOfFirst { it.id == current.id }
        val next = list.getOrNull(idx + 1) ?: list.firstOrNull() ?: return
        tuneToChannel(next)
    }

    fun channelDown() {
        val list = channels.value
        val current = _currentChannel.value ?: return
        val idx = list.indexOfFirst { it.id == current.id }
        val prev = list.getOrNull(idx - 1) ?: list.lastOrNull() ?: return
        tuneToChannel(prev)
    }

    fun toggleFavorite() {
        val channel = _currentChannel.value ?: return
        viewModelScope.launch {
            channelRepository.setFavorite(channel.id, !channel.isFavorite)
        }
    }

    /**
     * Parse a channel input string like "6.4" or "12.1" and tune to that
     * virtual channel if it exists in the database. Major-only input (e.g.
     * "7") tunes to the first sub-channel of that major (e.g. 7.1).
     */
    fun tuneToChannelInput(input: String) {
        val parts = input.trim().split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: return
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 1
        viewModelScope.launch {
            val channel = channelRepository.findByMajorMinor(major, minor) ?: return@launch
            tuneToChannel(channel)
        }
    }

    fun showOverlay() { _overlayVisible.value = true }
    fun hideOverlay() { _overlayVisible.value = false }
    fun toggleOverlay() { _overlayVisible.value = !_overlayVisible.value }

    private fun refreshSignalMetrics() {
        viewModelScope.launch {
            _signalMetrics.value = tunerManager.getSignalMetrics()
        }
    }

    override fun onCleared() {
        super.onCleared()
        playbackEngine.release()
    }
}
