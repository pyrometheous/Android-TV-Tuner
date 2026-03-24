package dev.tvtuner.tuner.core

/**
 * Descriptor for a discovered physical tuner device.
 */
data class TunerDevice(
    val id: String,
    val displayName: String,
    val backendType: TunerBackendType,
    /** Vendor ID (USB), or IP address (network) */
    val address: String,
    val isConnected: Boolean = false,
)

enum class TunerBackendType {
    USB_MYGICA,
    NETWORK_HDHR,
    FAKE,
}
