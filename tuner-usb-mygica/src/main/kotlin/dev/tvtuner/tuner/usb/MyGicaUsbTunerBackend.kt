package dev.tvtuner.tuner.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tvtuner.tuner.core.ScanEvent
import dev.tvtuner.tuner.core.ScanMode
import dev.tvtuner.tuner.core.SignalMetrics
import dev.tvtuner.tuner.core.TuneRequest
import dev.tvtuner.tuner.core.TunerBackend
import dev.tvtuner.tuner.core.TunerBackendType
import dev.tvtuner.tuner.core.TunerDevice
import dev.tvtuner.tuner.core.TunerError
import dev.tvtuner.tuner.core.TunerResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * USB tuner backend targeting the MyGica PT682C / PadTV HD ATSC USB-C tuner family.
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  HARDWARE INTEGRATION STATUS — INCOMPLETE                               │
 * │                                                                         │
 * │  The MyGica PT682C uses a vendor-specific USB protocol. The exact VID,  │
 * │  PID, control commands, and data pipe layout are NOT publicly           │
 * │  documented. Integration requires one of:                               │
 * │    A) MyGica vendor SDK / JNI library (contact MyGica/PadTV)            │
 * │    B) USB protocol analysis via Wireshark + usbmon on Linux or          │
 * │       Android UsbMonitor capturing the PadTV HD app traffic             │
 * │                                                                         │
 * │  All methods in this class return TunerError.NotImplemented until       │
 * │  the vendor protocol is mapped. The interface contract is stable;       │
 * │  only this implementation file needs filling in.                        │
 * │                                                                         │
 * │  See DEVELOPMENT_STATUS.md §USB Hardware Integration for next steps.    │
 * └─────────────────────────────────────────────────────────────────────────┘
 */
class MyGicaUsbTunerBackend @Inject constructor(
    @ApplicationContext private val context: Context,
) : TunerBackend {

    companion object {
        private const val TAG = "MyGicaUsb"
        private const val ACTION_USB_PERMISSION = "dev.tvtuner.USB_PERMISSION"

        /**
         * Known / suspected vendor IDs for the PT682C family.
         * MUST be confirmed against actual hardware via `adb shell lsusb`.
         * These are placeholders — see usb_device_filter.xml for the full list.
         */
        private val KNOWN_VENDOR_IDS = setOf(0x1f4d, 0x15a4, 0x048d)
    }

    override val backendName: String = TunerBackendType.USB_MYGICA.name

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    override suspend fun discoverTuners(): List<TunerDevice> {
        val devices = usbManager.deviceList.values
            .filter { it.vendorId in KNOWN_VENDOR_IDS }
            .map { usbDevice ->
                TunerDevice(
                    id = "usb-${usbDevice.deviceId}",
                    displayName = "MyGica USB Tuner (${usbDevice.productName ?: "Unknown"})",
                    backendType = TunerBackendType.USB_MYGICA,
                    address = "USB:${usbDevice.deviceName}",
                    isConnected = usbManager.hasPermission(usbDevice),
                )
            }
        Log.d(TAG, "discoverTuners: found ${devices.size} device(s)")
        return devices
    }

    override suspend fun requestPermission(device: TunerDevice): TunerResult<Unit> {
        val usbDevice = findUsbDevice(device)
            ?: return TunerResult.Failure(TunerError.DeviceNotFound("USB device not found: ${device.address}"))

        if (usbManager.hasPermission(usbDevice)) {
            return TunerResult.Success(Unit)
        }

        return suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    if (intent.action == ACTION_USB_PERMISSION) {
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        context.unregisterReceiver(this)
                        if (granted) {
                            cont.resume(TunerResult.Success(Unit))
                        } else {
                            cont.resume(TunerResult.Failure(TunerError.PermissionDenied("USB permission denied by user")))
                        }
                    }
                }
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val permIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), flags)

            context.registerReceiver(
                receiver,
                IntentFilter(ACTION_USB_PERMISSION),
                Context.RECEIVER_NOT_EXPORTED,
            )
            usbManager.requestPermission(usbDevice, permIntent)

            cont.invokeOnCancellation {
                try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            }
        }
    }

    override suspend fun openTuner(device: TunerDevice): TunerResult<Unit> {
        // Device has been discovered and permission granted at this point.
        // TODO: Open USB bulk/isochronous transfer endpoints
        // TODO: Send firmware init commands (vendor-specific control transfers)
        // TODO: Configure ATSC demodulator registers
        Log.w(TAG, "openTuner: device handle obtained — USB protocol init pending (see DEVELOPMENT_STATUS.md)")
        return TunerResult.Success(Unit)
    }

    override suspend fun closeTuner() {
        // TODO: Send close/reset commands, release USB connection
        Log.w(TAG, "closeTuner: NOT IMPLEMENTED")
    }

    override suspend fun tune(request: TuneRequest): TunerResult<Unit> {
        // TODO: Send tune command to demodulator (frequency, bandwidth)
        // TODO: Wait for lock signal confirmation
        Log.w(TAG, "tune: NOT IMPLEMENTED — vendor protocol required")
        return TunerResult.Failure(TunerError.NotImplemented())
    }

    override suspend fun stopStream() {
        // TODO: Halt USB isochronous/bulk transfers
        Log.w(TAG, "stopStream: NOT IMPLEMENTED")
    }

    override fun readTransportStream(): Flow<ByteArray> {
        // TODO: Read USB bulk/isochronous endpoint and emit 188-byte TS packets
        Log.w(TAG, "readTransportStream: NOT IMPLEMENTED")
        return emptyFlow()
    }

    override fun scanChannels(mode: ScanMode): Flow<ScanEvent> = callbackFlow {
        // TODO: Iterate ATSC broadcast frequencies (57–803 MHz in 6 MHz steps for US)
        // TODO: Tune to each, wait for lock, if locked — read PAT/PMT/MGT/VCT tables
        // TODO: Emit ScanEvent.ChannelFound for each discovered service
        Log.w(TAG, "scanChannels: NOT IMPLEMENTED — vendor protocol required")
        send(ScanEvent.Error(TunerError.NotImplemented()))
        awaitClose()
    }

    override suspend fun getSignalMetrics(): SignalMetrics {
        // TODO: Read SNR, BER, signal strength registers from demodulator
        return SignalMetrics(
            snrDb = null,
            qualityPercent = null,
            strengthDbm = null,
            isLocked = false,
        )
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun findUsbDevice(device: TunerDevice): UsbDevice? {
        // device.address format: "USB:/dev/bus/usb/XXX/YYY"
        val path = device.address.removePrefix("USB:")
        return usbManager.deviceList[path]
    }
}
