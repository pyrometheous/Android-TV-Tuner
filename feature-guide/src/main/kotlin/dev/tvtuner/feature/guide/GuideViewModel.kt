package dev.tvtuner.feature.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tvtuner.core.data.db.entity.ChannelEntity
import dev.tvtuner.core.data.db.entity.GuideEntryEntity
import dev.tvtuner.core.data.repository.ChannelRepository
import dev.tvtuner.core.data.repository.GuideRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class GuideViewModel @Inject constructor(
    channelRepository: ChannelRepository,
    private val guideRepository: GuideRepository,
) : ViewModel() {

    val channels: StateFlow<List<ChannelEntity>> = channelRepository
        .observeVisibleChannels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Guide window: now to now+4h */
    val guideWindowStart: Long = Instant.now().toEpochMilli()
    val guideWindowEnd: Long = Instant.now().plus(4, ChronoUnit.HOURS).toEpochMilli()

    val guideEntries: StateFlow<List<GuideEntryEntity>> = guideRepository
        .observeGuideWindow(
            // Initially empty list — refreshes when channels loads
            channelIds = emptyList(),
            fromMs = guideWindowStart,
            toMs = guideWindowEnd,
        )
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedChannelId = MutableStateFlow<Long?>(null)
    val selectedChannelId: StateFlow<Long?> = _selectedChannelId.asStateFlow()

    fun selectChannel(channelId: Long) {
        _selectedChannelId.value = channelId
    }
}
