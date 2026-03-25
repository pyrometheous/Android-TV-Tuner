package dev.tvtuner.tuner.core

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
/**
 * Manages the active [TunerBackend] and provides app-wide tuner state.
 *
 * Backends are registered at startup; the manager selects the most appropriate
 * one based on user preferences and device availability.
 */
@Singleton
class TunerManager @Inject constructor(
    private val backends: @JvmSuppressWildcards Set<TunerBackend>,
) {
    companion object {
        private const val TAG = "TunerManager"
    }

    private val _state = MutableStateFlow<TunerState>(TunerState.Idle)
    val state: StateFlow<TunerState> = _state.asStateFlow()

    private var activeBackend: TunerBackend? = null
    private var activeDevice: TunerDevice? = null

    suspend fun discoverAll(): List<TunerDevice> {
        val devices = mutableListOf<TunerDevice>()
        backends.forEach { backend ->
            try {
                devices += backend.discoverTuners()
            } catch (e: Exception) {
                Log.e(TAG, "Discovery failed for ${backend.backendName}", e)
            }
        }
        return devices
    }

    suspend fun select(device: TunerDevice): TunerResult<Unit> {
        val backend = backends.firstOrNull { it.backendName == device.backendType.name }
            ?: return TunerResult.Failure(TunerError.DeviceNotFound("No backend for ${device.backendType}"))

        return try {
            val permResult = backend.requestPermission(device)
            if (permResult is TunerResult.Failure) return permResult

            val openResult = backend.openTuner(device)
            if (openResult is TunerResult.Failure) {
                _state.value = TunerState.Error(openResult.error)
                return openResult
            }

            activeBackend = backend
            activeDevice = device
            _state.value = TunerState.Ready(device)
            Log.i(TAG, "Tuner selected: ${device.displayName} via ${backend.backendName}")
            TunerResult.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "select() failed unexpectedly", e)
            val error = TunerError.Unknown(e.message ?: "Unexpected error during device selection", e)
            _state.value = TunerState.Error(error)
            TunerResult.Failure(error)
        }
    }

    suspend fun tune(request: TuneRequest): TunerResult<Unit> {
        val backend = activeBackend
            ?: return TunerResult.Failure(TunerError.DeviceNotFound("No active tuner"))
        val result = backend.tune(request)
        if (result is TunerResult.Success) {
            _state.value = TunerState.Tuned(activeDevice!!, request)
        }
        return result
    }

    fun readTransportStream(): Flow<ByteArray> {
        val backend = activeBackend ?: run {
            Log.w(TAG, "readTransportStream called with no active tuner")
            return emptyFlow()
        }
        return backend.readTransportStream()
    }

    fun scanChannels(mode: ScanMode = ScanMode.FULL): Flow<ScanEvent> {
        val backend = activeBackend ?: return flow {
            emit(ScanEvent.Error(TunerError.DeviceNotFound(
                "No tuner selected. Connect your tuner and select it before scanning."
            )))
        }
        return backend.scanChannels(mode)
    }

    suspend fun getSignalMetrics(): SignalMetrics? = try {
        activeBackend?.getSignalMetrics()
    } catch (e: Exception) {
        Log.e(TAG, "getSignalMetrics failed", e)
        null
    }

    suspend fun stopStream() {
        activeBackend?.stopStream()
    }

    suspend fun close() {
        activeBackend?.closeTuner()
        activeBackend = null
        activeDevice = null
        _state.value = TunerState.Idle
    }

    fun getActiveBackend(): TunerBackend? = activeBackend

    /**
     * Look for any USB tuner already connected (i.e. USB permission already granted).
     * If found and no tuner is currently active, select it automatically without
     * requiring user interaction.
     *
     * Called:
     *   • On app start (SettingsViewModel.init) — picks up a device that was
     *     plugged in before the app launched.
     *   • When USB_DEVICE_ATTACHED fires (MainActivity) — picks up a newly
     *     inserted device; if permission was previously granted it connects
     *     instantly; otherwise the USB permission dialog appears once.
     *
     * @return true if a USB device was found and selection was attempted.
     */
    suspend fun autoSelectUsbIfAvailable(): Boolean {
        if (_state.value is TunerState.Ready || _state.value is TunerState.Tuned) {
            Log.d(TAG, "autoSelectUsb: tuner already active, skipping")
            return false
        }
        val usbBackend = backends.firstOrNull {
            it.backendName == TunerBackendType.USB_MYGICA.name
        } ?: return false

        val devices = try { usbBackend.discoverTuners() } catch (e: Exception) { emptyList() }
        val device  = devices.firstOrNull() ?: run {
            Log.d(TAG, "autoSelectUsb: no USB tuner found")
            return false
        }

        Log.i(TAG, "autoSelectUsb: found ${device.displayName}, selecting…")
        select(device)   // triggers permission dialog if first-time, silent if already granted
        return true
    }
}

sealed class TunerState {
    data object Idle : TunerState()
    data class Ready(val device: TunerDevice) : TunerState()
    data class Tuned(val device: TunerDevice, val request: TuneRequest) : TunerState()
    data class Error(val error: TunerError) : TunerState()
}
