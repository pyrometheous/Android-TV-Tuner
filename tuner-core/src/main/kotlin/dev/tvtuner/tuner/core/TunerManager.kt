package dev.tvtuner.tuner.core

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

        val permResult = backend.requestPermission(device)
        if (permResult is TunerResult.Failure) return permResult

        val openResult = backend.openTuner(device)
        if (openResult is TunerResult.Failure) return openResult

        activeBackend = backend
        activeDevice = device
        _state.value = TunerState.Ready(device)
        Log.i(TAG, "Tuner selected: ${device.displayName} via ${backend.backendName}")
        return TunerResult.Success(Unit)
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
        val backend = activeBackend
            ?: throw IllegalStateException("No active tuner — call select() first")
        return backend.readTransportStream()
    }

    fun scanChannels(mode: ScanMode = ScanMode.FULL): Flow<ScanEvent> {
        val backend = activeBackend
            ?: throw IllegalStateException("No active tuner — call select() first")
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
}

sealed class TunerState {
    data object Idle : TunerState()
    data class Ready(val device: TunerDevice) : TunerState()
    data class Tuned(val device: TunerDevice, val request: TuneRequest) : TunerState()
    data class Error(val error: TunerError) : TunerState()
}
