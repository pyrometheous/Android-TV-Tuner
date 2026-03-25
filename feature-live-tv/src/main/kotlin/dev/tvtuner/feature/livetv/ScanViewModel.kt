package dev.tvtuner.feature.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tvtuner.core.data.db.entity.ChannelEntity
import dev.tvtuner.core.data.repository.ChannelRepository
import dev.tvtuner.tuner.core.ScanEvent
import dev.tvtuner.tuner.core.ScanMode
import dev.tvtuner.tuner.core.TunerError
import dev.tvtuner.tuner.core.TunerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val tunerManager: TunerManager,
    private val channelRepository: ChannelRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val foundChannels = mutableListOf<ScanEvent.ChannelFound>()

    fun startScan(mode: ScanMode = ScanMode.FULL) {
        if (_uiState.value is ScanUiState.Scanning) return

        if (tunerManager.getActiveBackend() == null) {
            _uiState.value = ScanUiState.Error(
                "No tuner selected. Plug in your MyGica tuner and wait for it to connect, then scan again."
            )
            return
        }

        _uiState.value = ScanUiState.Scanning(progress = 0, channelsFound = 0, currentFreqKhz = 0)
        foundChannels.clear()

        viewModelScope.launch {
            tunerManager.scanChannels(mode)
                .catch { e ->
                    _uiState.value = ScanUiState.Error("Scan failed: ${e.message}")
                }
                .collect { event ->
                    when (event) {
                        is ScanEvent.Progress -> {
                            val current = _uiState.value
                            if (current is ScanUiState.Scanning) {
                                _uiState.value = current.copy(
                                    progress = event.percentComplete,
                                    currentFreqKhz = event.rfChannelKhz,
                                )
                            }
                        }
                        is ScanEvent.ChannelFound -> {
                            foundChannels += event
                            val current = _uiState.value
                            if (current is ScanUiState.Scanning) {
                                _uiState.value = current.copy(channelsFound = foundChannels.size)
                            }
                        }
                        ScanEvent.Complete -> {
                            persistChannels()
                            _uiState.value = ScanUiState.Complete(foundChannels.size)
                        }
                        is ScanEvent.Error -> {
                            _uiState.value = ScanUiState.Error(event.error.userFacingMessage())
                        }
                        is ScanEvent.SignalStrength -> { /* informational */ }
                    }
                }
        }
    }

    private suspend fun persistChannels() {
        val entities = foundChannels.map { found ->
            ChannelEntity(
                rfChannelKhz = found.rfChannelKhz,
                majorChannel = found.majorChannel,
                minorChannel = found.minorChannel,
                callsign = found.callsign,
                serviceName = found.serviceName,
                programNumber = found.programNumber,
                isEncrypted = found.isEncrypted,
            )
        }
        channelRepository.replaceAll(entities)
    }
}

private fun TunerError.userFacingMessage(): String = when (this) {
    is TunerError.TuneFailed     -> "Scan error: $message"
    is TunerError.StreamError    -> "Stream error during scan: $message"
    else                         -> message
}

sealed class ScanUiState {
    data object Idle : ScanUiState()
    data class Scanning(
        val progress: Int,
        val channelsFound: Int,
        val currentFreqKhz: Int,
    ) : ScanUiState()
    data class Complete(val totalFound: Int) : ScanUiState()
    data class Error(val message: String) : ScanUiState()
}
