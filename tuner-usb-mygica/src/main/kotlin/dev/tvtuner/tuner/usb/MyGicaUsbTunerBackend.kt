package dev.tvtuner.tuner.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tvtuner.parser.atsc.PsipEvent
import dev.tvtuner.parser.atsc.PsipProcessor
import dev.tvtuner.parser.atsc.TsConstants
import dev.tvtuner.tuner.core.ScanEvent
import dev.tvtuner.tuner.core.ScanMode
import dev.tvtuner.tuner.core.SignalMetrics
import dev.tvtuner.tuner.core.TuneRequest
import dev.tvtuner.tuner.core.TunerBackend
import dev.tvtuner.tuner.core.TunerBackendType
import dev.tvtuner.tuner.core.TunerDevice
import dev.tvtuner.tuner.core.TunerError
import dev.tvtuner.tuner.core.TunerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * USB tuner backend for MyGica PT682C / PadTV HD ATSC USB-C tuner family.
 *
 * Implements the full ATSC pipeline:
 *   discoverTuners() → requestPermission() → openTuner()
 *   → tune() / scanChannels() → readTransportStream()
 *
 * Uses the ITE IT913x / AF9035 command transport (see [IteCommandTransport]).
 * Register addresses are based on the Linux AF9035 + IT913x kernel driver.
 * Run `adb shell cat /dev/usbmon0` or Wireshark usbmon to confirm addresses
 * if the device uses a different chip variant.
 */
class MyGicaUsbTunerBackend @Inject constructor(
    @ApplicationContext private val context: Context,
) : TunerBackend {

    companion object {
        private const val TAG = "MyGicaUsb"
        private const val ACTION_USB_PERMISSION = "dev.tvtuner.USB_PERMISSION"

        /**
         * Known Vendor IDs for the PT682C family.
         * VID 0x1f4d = Geniatech/GoTView (MyGica parent brand)
         * VID 0x15a4 = Afatech (AF9035 chips)
         * VID 0x048d = ITE Technologies (IT913x chips)
         * Confirm the actual VID/PID with: adb shell lsusb
         */
        private val KNOWN_VENDOR_IDS = setOf(0x1f4d, 0x15a4, 0x048d)

        /** Maximum TS bytes read per USB transfer (32 packets × 188 bytes). */
        private const val USB_READ_CHUNK = 32 * 188   // 6016 bytes

        /** Bulk transfer timeout in ms; short enough for responsive cancellation. */
        private const val USB_TIMEOUT_MS = 1_000

        /**
         * How long to collect PSIP tables after signal is confirmed.
         * ATSC A/65 allows up to ~5 s between TVCT repetitions in practice;
         * 8 s gives two full cycles on slow broadcasters.
         */
        private const val PSIP_COLLECTION_MS = 8_000L

        /**
         * Window used to probe for live TS sync bytes after a frequency change.
         * Replaces register-based lock detection (register address uncertain
         * across ITE chip variants). 1 second is enough to detect a carrier.
         */
        private const val TS_PROBE_MS = 1_000L

        /** Minimum aligned 0x47 sync bytes required to confirm a live TS signal. */
        private const val TS_PROBE_MIN_SYNCS = 3
    }

    override val backendName: String = TunerBackendType.USB_MYGICA.name

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    // Active connection state — set in openTuner(), cleared in closeTuner()
    @Volatile private var connection: UsbDeviceConnection? = null
    @Volatile private var usbInterface: UsbInterface? = null
    @Volatile private var transport: IteCommandTransport? = null

    // ─── Discovery ────────────────────────────────────────────────────────────

    override suspend fun discoverTuners(): List<TunerDevice> {
        val devices = usbManager.deviceList.values
            .filter { it.vendorId in KNOWN_VENDOR_IDS }
            .map { usbDevice ->
                TunerDevice(
                    id = "usb-${usbDevice.deviceId}",
                    displayName = buildDisplayName(usbDevice),
                    backendType = TunerBackendType.USB_MYGICA,
                    address = "USB:${usbDevice.deviceName}",
                    isConnected = usbManager.hasPermission(usbDevice),
                )
            }
        Log.d(TAG, "discoverTuners: found ${devices.size} device(s)")
        return devices
    }

    // ─── Permission ──────────────────────────────────────────────────────────

    override suspend fun requestPermission(device: TunerDevice): TunerResult<Unit> {
        val usbDevice = findUsbDevice(device)
            ?: return TunerResult.Failure(TunerError.DeviceNotFound("USB device not found: ${device.address}"))

        if (usbManager.hasPermission(usbDevice)) {
            return TunerResult.Success(Unit)
        }

        return try {
            suspendCancellableCoroutine { cont ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context, intent: Intent) {
                        if (intent.action == ACTION_USB_PERMISSION) {
                            val granted = intent.getBooleanExtra(
                                UsbManager.EXTRA_PERMISSION_GRANTED, false)
                            try { context.unregisterReceiver(this) } catch (_: Exception) {}
                            if (granted) {
                                cont.resume(TunerResult.Success(Unit))
                            } else {
                                cont.resume(
                                    TunerResult.Failure(
                                        TunerError.PermissionDenied("USB permission denied by user")))
                            }
                        }
                    }
                }

                val permissionIntent = Intent(ACTION_USB_PERMISSION)
                    .setPackage(context.packageName)
                val pendingIntent = PendingIntent.getBroadcast(
                    context, 0, permissionIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

                try {
                    context.registerReceiver(
                        receiver,
                        IntentFilter(ACTION_USB_PERMISSION),
                        Context.RECEIVER_NOT_EXPORTED)
                    usbManager.requestPermission(usbDevice, pendingIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "requestPermission: setup failed — ${e.message}")
                    try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
                    cont.resumeWith(Result.failure(e))
                }

                cont.invokeOnCancellation {
                    try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "requestPermission: failed", e)
            TunerResult.Failure(
                TunerError.DeviceOpenFailed("USB permission request failed: ${e.message}", e))
        }
    }

    // ─── Open / Close ─────────────────────────────────────────────────────────

    override suspend fun openTuner(device: TunerDevice): TunerResult<Unit> {
        val usbDevice = findUsbDevice(device)
            ?: return TunerResult.Failure(
                TunerError.DeviceNotFound("USB device disappeared: ${device.address}"))

        return withContext(Dispatchers.IO) {
            try {
                val conn = usbManager.openDevice(usbDevice)
                    ?: return@withContext TunerResult.Failure(
                        TunerError.DeviceOpenFailed("usbManager.openDevice() returned null"))

                // Most ITE-based devices put everything on interface 0.
                // Alt-setting 1 enables TS streaming on some variants; we try
                // to set it and fall back silently if not supported.
                val iface = usbDevice.getInterface(0)
                val claimed = conn.claimInterface(iface, true)
                if (!claimed) {
                    conn.close()
                    return@withContext TunerResult.Failure(
                        TunerError.DeviceOpenFailed("Could not claim USB interface 0"))
                }

                // Try alternate setting 1 to enable the TS bulk endpoint on
                // devices that separate control and streaming alt-settings.
                if (usbDevice.getInterface(0).let { iface0 ->
                        (0 until usbDevice.interfaceCount).any { idx ->
                            val candidate = usbDevice.getInterface(idx)
                            candidate.id == iface0.id && candidate.alternateSetting == 1
                        }
                    }) {
                    conn.setInterface(usbDevice.getInterface(
                        (0 until usbDevice.interfaceCount).first { idx ->
                            usbDevice.getInterface(idx).alternateSetting == 1
                        }))
                    Log.d(TAG, "openTuner: switched to alt-setting 1")
                }

                val endpoints = discoverEndpoints(iface)
                if (endpoints == null) {
                    conn.releaseInterface(iface)
                    conn.close()
                    return@withContext TunerResult.Failure(
                        TunerError.DeviceOpenFailed(
                            "Could not find required USB bulk endpoints on interface 0"))
                }
                val (epCmdOut, epCmdIn, epTsIn) = endpoints

                Log.d(TAG, "openTuner: CMD-OUT=0x${epCmdOut.address.toByte().toHex()} " +
                        "CMD-IN=0x${epCmdIn.address.toByte().toHex()} " +
                        "TS-IN=0x${epTsIn.address.toByte().toHex()}")

                val xport = IteCommandTransport(conn, epCmdOut, epCmdIn, epTsIn)
                val fwVer = xport.readFirmwareVersion()
                Log.i(TAG, "openTuner: ITE firmware v$fwVer connected — " +
                        "${usbDevice.productName ?: "MyGica tuner"}")

                connection  = conn
                usbInterface = iface
                transport   = xport

                TunerResult.Success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "openTuner: failed", e)
                TunerResult.Failure(TunerError.DeviceOpenFailed(e.message ?: "USB open failed", e))
            }
        }
    }

    override suspend fun closeTuner() {
        transport?.disableStream()
        transport = null
        usbInterface?.let { iface ->
            try { connection?.releaseInterface(iface) } catch (_: Exception) {}
        }
        usbInterface = null
        try { connection?.close() } catch (_: Exception) {}
        connection = null
        Log.i(TAG, "closeTuner: USB connection released")
    }

    // ─── Tune ─────────────────────────────────────────────────────────────────

    override suspend fun tune(request: TuneRequest): TunerResult<Unit> {
        val xport = transport
            ?: return TunerResult.Failure(TunerError.DeviceOpenFailed("Tuner not open"))

        Log.d(TAG, "tune: ${request.rfChannelKhz} kHz, program ${request.programNumber}")

        val tuneOk = xport.setFrequency(request.rfChannelKhz)
        if (!tuneOk) {
            // Log but do not hard-fail: if the register address is wrong for this
            // chip variant the write is a no-op, but the device may already be
            // on the correct frequency or may respond to it differently.
            Log.w(TAG, "tune: setFrequency() returned false — continuing anyway")
        }

        // Allow demodulator time to acquire carrier before starting stream
        delay(500)
        xport.enableStream()
        Log.i(TAG, "tune: stream enabled at ${request.rfChannelKhz} kHz")
        return TunerResult.Success(Unit)
    }

    override suspend fun stopStream() {
        transport?.disableStream()
    }

    // ─── Transport Stream ─────────────────────────────────────────────────────

    /**
     * Read raw 188-byte MPEG-2 TS packets from the USB bulk endpoint.
     *
     * The flow:
     *   1. Reads up to [USB_READ_CHUNK] bytes per USB transfer.
     *   2. Scans the received data for 0x47 sync bytes.
     *   3. Emits valid 188-byte TS packet slices to collectors.
     *
     * Collects indefinitely until:
     *   - The coroutine scope is cancelled (normal stop), or
     *   - The USB connection is lost (flow terminates with final disableStream).
     */
    override fun readTransportStream(): Flow<ByteArray> = flow {
        val xport = transport ?: return@flow
        val conn  = connection ?: return@flow
        val ep    = xport.epTsIn

        xport.enableStream()
        val buf = ByteArray(USB_READ_CHUNK)

        try {
            while (currentCoroutineContext().isActive) {
                val len = withContext(Dispatchers.IO) {
                    conn.bulkTransfer(ep, buf, buf.size, USB_TIMEOUT_MS)
                }
                if (len <= 0) continue  // timeout or transient error — retry

                // Scan buffer for TS sync bytes and emit 188-byte packets
                var i = 0
                while (i + TsConstants.PACKET_SIZE <= len) {
                    if (buf[i] == TsConstants.SYNC_BYTE) {
                        emit(buf.copyOfRange(i, i + TsConstants.PACKET_SIZE))
                        i += TsConstants.PACKET_SIZE
                    } else {
                        i++   // re-sync: walk byte-by-byte until next 0x47
                    }
                }
            }
        } finally {
            withContext(Dispatchers.IO) { xport.disableStream() }
        }
    }

    // ─── Channel Scan ──────────────────────────────────────────────────────────

    /**
     * Scan all ATSC frequencies for active broadcast channels.
     *
     * For each RF frequency:
     *   1. Tune the demodulator.
     *   2. Wait up to [LOCK_TIMEOUT_MS] ms for signal lock.
     *   3. If locked, read the TS for up to [PSIP_COLLECTION_MS] ms and
     *      parse PSIP tables (MGT → VCT section) using [PsipProcessor].
     *   4. Emit a [ScanEvent.ChannelFound] for each virtual channel found.
     *
     * A fresh [PsipProcessor] is used per frequency to avoid stale state.
     *
     * Signal detection uses a TS sync-byte probe rather than a lock register
     * read, because the register address differs between ITE chip variants and
     * may not respond correctly without confirmed hardware knowledge.
     */
    override fun scanChannels(mode: ScanMode): Flow<ScanEvent> = flow {
        val xport = transport
        val conn  = connection
        if (xport == null || conn == null) {
            emit(ScanEvent.Error(TunerError.DeviceOpenFailed("USB tuner not open for scan")))
            return@flow
        }

        val plan  = if (mode == ScanMode.QUICK) AtscFrequencyPlan.QUICK else AtscFrequencyPlan.ALL
        val total = plan.size
        val ep    = xport.epTsIn
        val readBuf = ByteArray(USB_READ_CHUNK)

        for ((index, rfCh) in plan.withIndex()) {
            if (!currentCoroutineContext().isActive) break

            val freqKhz = rfCh.centerFreqKhz
            emit(ScanEvent.Progress(freqKhz, (index * 100) / total))
            Log.d(TAG, "scan: ch ${rfCh.rfChannel} @ $freqKhz kHz")

            // Write frequency to demodulator registers
            xport.setFrequency(freqKhz)

            // Allow demodulator ~400 ms to begin acquiring the new carrier
            delay(400)

            // Enable TS output; then probe for live sync bytes.
            // This is hardware-agnostic: if we see ≥TS_PROBE_MIN_SYNCS aligned
            // 0x47 bytes within TS_PROBE_MS, a signal is present regardless of
            // whether the lock register read succeeded.
            xport.enableStream()
            val syncs = probeForTsSync(conn, ep, readBuf)
            if (syncs < TS_PROBE_MIN_SYNCS) {
                Log.d(TAG, "scan: no signal on ch ${rfCh.rfChannel} ($syncs syncs)")
                xport.disableStream()
                continue
            }
            Log.i(TAG, "scan: signal on ch ${rfCh.rfChannel} ($syncs syncs) — collecting PSIP")

            // Signal confirmed — collect PSIP tables for up to PSIP_COLLECTION_MS
            val psip = PsipProcessor()
            val foundPrograms = mutableSetOf<Int>()
            val deadline = System.currentTimeMillis() + PSIP_COLLECTION_MS

            while (System.currentTimeMillis() < deadline && currentCoroutineContext().isActive) {
                val len = withContext(Dispatchers.IO) {
                    conn.bulkTransfer(ep, readBuf, readBuf.size, 500)
                }
                if (len <= 0) continue

                var i = 0
                while (i + TsConstants.PACKET_SIZE <= len) {
                    if (readBuf[i] == TsConstants.SYNC_BYTE) {
                        val packet = readBuf.copyOfRange(i, i + TsConstants.PACKET_SIZE)
                        psip.process(packet).forEach { event ->
                            if (event is PsipEvent.VctParsed) {
                                event.vct.channels.forEach { vctCh ->
                                    if (foundPrograms.add(vctCh.programNumber)) {
                                        Log.i(TAG, "scan: found ${vctCh.shortName} " +
                                                "${vctCh.majorChannelNumber}.${vctCh.minorChannelNumber}")
                                        emit(ScanEvent.ChannelFound(
                                            rfChannelKhz  = freqKhz,
                                            programNumber = vctCh.programNumber,
                                            majorChannel  = vctCh.majorChannelNumber,
                                            minorChannel  = vctCh.minorChannelNumber,
                                            callsign      = vctCh.shortName,
                                            serviceName   = vctCh.shortName,
                                            isEncrypted   = vctCh.accessControlled,
                                        ))
                                    }
                                }
                            }
                        }
                        i += TsConstants.PACKET_SIZE
                    } else {
                        i++
                    }
                }
            }
            xport.disableStream()
        }

        emit(ScanEvent.Progress(0, 100))
        emit(ScanEvent.Complete)
        Log.i(TAG, "scan: complete")
    }

    // ─── Signal Metrics ───────────────────────────────────────────────────────

    /**
     * Read USB bulk data for [TS_PROBE_MS] ms and count aligned TS 0x47 sync bytes.
     *
     * Using sync-byte counting rather than a register read makes this agnostic
     * to chip variants: if the demodulator locked and is outputting a valid TS,
     * we'll see repeating 0x47 bytes at 188-byte intervals.
     */
    private suspend fun probeForTsSync(
        conn: UsbDeviceConnection,
        ep: UsbEndpoint,
        buf: ByteArray,
    ): Int = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + TS_PROBE_MS
        var syncCount = 0
        while (System.currentTimeMillis() < deadline) {
            val len = conn.bulkTransfer(ep, buf, buf.size, 400)
            if (len <= 0) continue
            var i = 0
            while (i + TsConstants.PACKET_SIZE <= len) {
                if (buf[i] == TsConstants.SYNC_BYTE) {
                    syncCount++
                    if (syncCount >= TS_PROBE_MIN_SYNCS) return@withContext syncCount
                    i += TsConstants.PACKET_SIZE
                } else {
                    i++
                }
            }
        }
        syncCount
    }

    override suspend fun getSignalMetrics(): SignalMetrics {
        val xport = transport ?: return SignalMetrics(null, null, null, false)
        val lockStatus = xport.readReg(IteCommandTransport.REG_LOCK_STATUS) ?: return SignalMetrics(null, null, null, false)
        val isLocked = (lockStatus and 0x08) != 0
        return SignalMetrics(
            snrDb          = null,   // TODO: read SNR register once address confirmed
            qualityPercent = if (isLocked) 80 else 0,
            strengthDbm    = null,
            isLocked       = isLocked,
        )
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun buildDisplayName(usbDevice: UsbDevice): String {
        val product = usbDevice.productName
        return when {
            !product.isNullOrBlank() -> "MyGica $product"
            else -> "MyGica USB ATSC Tuner (VID:0x${usbDevice.vendorId.toString(16).padStart(4,'0')})"
        }
    }

    private fun findUsbDevice(device: TunerDevice): UsbDevice? {
        val path = device.address.removePrefix("USB:")
        return usbManager.deviceList[path]
    }

    /**
     * Locate the three USB bulk endpoints required for ITE operation.
     *
     * Strategy:
     *   - cmdOut (EP_CMD_OUT): only Bulk-OUT endpoint on the interface
     *   - tsIn   (EP_TS_IN):   Bulk-IN endpoint with address 0x84, or the
     *                           Bulk-IN with the highest address if 0x84 absent
     *   - cmdIn  (EP_CMD_IN):  Bulk-IN endpoint that is NOT the TS endpoint
     *
     * Returns null if the minimum requirements (at least 1 OUT + 2 IN, or
     * 1 OUT + 1 IN if TS and cmd share an endpoint) can't be met.
     */
    private fun discoverEndpoints(iface: UsbInterface): Triple<UsbEndpoint, UsbEndpoint, UsbEndpoint>? {
        val bulk = (0 until iface.endpointCount)
            .map { iface.getEndpoint(it) }
            .filter { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK }

        val outs = bulk.filter { it.direction == UsbConstants.USB_DIR_OUT }
        val ins  = bulk.filter { it.direction == UsbConstants.USB_DIR_IN }

        val cmdOut = outs.firstOrNull()            ?: return null.also { Log.e(TAG, "No Bulk-OUT endpoint found") }
        val tsIn   = ins.firstOrNull { it.address == 0x84 }
                  ?: ins.maxByOrNull { it.address }
                  ?: return null.also { Log.e(TAG, "No Bulk-IN endpoint found") }
        val cmdIn  = ins.firstOrNull { it.address != tsIn.address }
                  ?: tsIn  // fallback: share the same IN endpoint

        return Triple(cmdOut, cmdIn, tsIn)
    }

    private fun Byte.toHex() = (toInt() and 0xFF).toString(16).padStart(2, '0')
}
