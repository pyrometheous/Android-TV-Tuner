package dev.tvtuner.feature.recordings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tvtuner.core.data.db.entity.RecordingEntity
import dev.tvtuner.core.data.repository.RecordingRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordingsViewModel @Inject constructor(
    private val recordingRepository: RecordingRepository,
    private val recordingManager: RecordingManager,
) : ViewModel() {

    val recordings: StateFlow<List<RecordingEntity>> = recordingRepository
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeRecordings: StateFlow<List<RecordingEntity>> = recordingRepository
        .observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteRecording(id: Long) {
        viewModelScope.launch {
            recordingManager.deleteRecording(id)
        }
    }

    fun updateWatchedProgress(id: Long, posMs: Long, isWatched: Boolean) {
        viewModelScope.launch {
            recordingRepository.updateWatchedProgress(id, posMs, isWatched)
        }
    }
}
