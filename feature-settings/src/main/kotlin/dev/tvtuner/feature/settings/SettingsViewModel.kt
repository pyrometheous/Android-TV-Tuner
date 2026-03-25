package dev.tvtuner.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tvtuner.core.data.preferences.AppPreferences
import dev.tvtuner.core.data.repository.ChannelRepository
import dev.tvtuner.tuner.core.TunerDevice
import dev.tvtuner.tuner.core.TunerManager
import dev.tvtuner.tuner.core.TunerState
import dev.tvtuner.tuner.core.TunerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val tunerManager: TunerManager,
    private val channelRepository: ChannelRepository,
) : ViewModel() {

    val selectedBackend: StateFlow<String> = prefs.selectedTunerBackend
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPreferences.TunerBackend.AUTO)

    val networkTunerUrl: StateFlow<String?> = prefs.networkTunerUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recordingPath: StateFlow<String?> = prefs.recordingStoragePath
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _discoveredTuners = MutableStateFlow<List<TunerDevice>>(emptyList())
    val discoveredTuners: StateFlow<List<TunerDevice>> = _discoveredTuners.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    val tunerState: StateFlow<TunerState> = tunerManager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TunerState.Idle)

    private val _selectError = MutableStateFlow<String?>(null)
    val selectError: StateFlow<String?> = _selectError.asStateFlow()

    fun scanForNetworkTuners() {
        viewModelScope.launch {
            _isScanning.value = true
            _discoveredTuners.value = tunerManager.discoverAll()
            _isScanning.value = false
        }
    }

    fun selectDevice(device: TunerDevice) {
        viewModelScope.launch {
            _selectError.value = null
            val result = tunerManager.select(device)
            if (result is TunerResult.Failure) {
                _selectError.value = result.error.message
            }
        }
    }

    fun clearSelectError() {
        _selectError.value = null
    }

    fun selectTunerBackend(backend: String) {
        viewModelScope.launch { prefs.setSelectedTunerBackend(backend) }
    }

    fun setNetworkTunerUrl(url: String) {
        viewModelScope.launch { prefs.setNetworkTunerUrl(url) }
    }

    fun setRecordingPath(path: String) {
        viewModelScope.launch { prefs.setRecordingStoragePath(path) }
    }

    val channelCount: StateFlow<Int> = MutableStateFlow(0).also { flow ->
        viewModelScope.launch {
            flow.value = channelRepository.count()
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch { prefs.setOnboardingComplete(true) }
    }
}
