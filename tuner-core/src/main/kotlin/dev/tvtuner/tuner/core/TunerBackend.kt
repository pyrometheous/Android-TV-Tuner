package dev.tvtuner.tuner.core

import kotlinx.coroutines.flow.Flow

/**
 * Core abstraction for all tuner backends.
 *
 * Implementations:
 *   - [dev.tvtuner.tuner.usb.MyGicaUsbTunerBackend] — USB-C hardware (PT682C family)
 *   - [dev.tvtuner.tuner.network.HdhrTunerBackend] — HDHomeRun network tuner
 *
 * Threading: All suspend functions are safe to call from any coroutine context.
 * Flows are cold and will start work only when collected.
 */
interface TunerBackend {

    /** Human-readable name for this backend type. */
    val backendName: String

    /**
     * Discover available tuner devices of this backend's type.
     * Returns an empty list if none are found (not an error).
     */
    suspend fun discoverTuners(): List<TunerDevice>

    /**
     * Request permission to use the given device if required (e.g. USB permission dialog).
     * Returns [TunerResult.Success] with [Unit] if permission is already granted.
     */
    suspend fun requestPermission(device: TunerDevice): TunerResult<Unit>

    /**
     * Open the device and prepare it for tuning.
     * Must be called before [tune].
     */
    suspend fun openTuner(device: TunerDevice): TunerResult<Unit>

    /**
     * Close the device and release all resources.
     */
    suspend fun closeTuner()

    /**
     * Tune to a specific RF channel/program.
     * Device must be opened first.
     */
    suspend fun tune(request: TuneRequest): TunerResult<Unit>

    /**
     * Stop the active stream without closing the device.
     */
    suspend fun stopStream()

    /**
     * Start the transport stream for the currently tuned channel.
     * Emits raw MPEG-2 TS 188-byte packets.
     * Cancelling the collector stops the stream.
     */
    fun readTransportStream(): Flow<ByteArray>

    /**
     * Scan for broadcast channels.
     * Emits [ScanEvent]s as progress occurs.
     * The flow completes when the scan finishes.
     */
    fun scanChannels(mode: ScanMode = ScanMode.FULL): Flow<ScanEvent>

    /**
     * Read the current signal quality metrics for the tuned channel.
     * Returns null values for metrics the hardware does not expose.
     */
    suspend fun getSignalMetrics(): SignalMetrics
}

enum class ScanMode { FULL, QUICK }

sealed class ScanEvent {
    data class Progress(
        val rfChannelKhz: Int,
        val percentComplete: Int,
        /** RF channel number (e.g. 14 for UHF ch 14); 0 if unknown. */
        val rfChannelNumber: Int = 0,
    ) : ScanEvent()
    data class ChannelFound(
        val rfChannelKhz: Int,
        val programNumber: Int,
        val majorChannel: Int,
        val minorChannel: Int,
        val callsign: String,
        val serviceName: String,
        val isEncrypted: Boolean,
    ) : ScanEvent()
    data class SignalStrength(val rfChannelKhz: Int, val metrics: SignalMetrics) : ScanEvent()
    data object Complete : ScanEvent()
    data class Error(val error: TunerError) : ScanEvent()
}
