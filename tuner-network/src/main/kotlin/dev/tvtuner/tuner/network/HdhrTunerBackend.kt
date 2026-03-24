package dev.tvtuner.tuner.network

import android.util.Log
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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.inject.Inject

/**
 * Network tuner backend for HDHomeRun devices (including Flex 4K ATSC 3.0).
 *
 * The HDHomeRun uses a well-documented HTTP + UDP protocol:
 *   - Discovery: UDP broadcast on port 65001, or HTTP GET /discover.json
 *   - Device info: HTTP GET http://<ip>/discover.json
 *   - Lineup: HTTP GET http://<ip>/lineup.json
 *   - Tune + stream: HTTP GET http://<ip>/auto/v<channel> streaming MPEG-TS
 *   - Signal info: HTTP GET http://<ip>/status.json (per tuner)
 *
 * This implementation provides discovery and stream URL retrieval.
 * The actual transport stream is pulled via Media3 / ExoPlayer using the HTTP URL;
 * we don't read raw UDP here since ExoPlayer handles the HTTP stream natively.
 *
 * ATSC 3.0 note: ATSC 3.0 streams from HDHomeRun Flex 4K require the device
 * firmware to provide an MPEG-TS compatible output. As of 2025, HDHomeRun
 * firmware handles this transparently for the HTTP stream endpoint.
 */
class HdhrTunerBackend @Inject constructor() : TunerBackend {

    companion object {
        private const val TAG = "HdhrTuner"
        private const val HDHR_DISCOVER_PORT = 65001
        private const val HDHR_DISCOVER_TIMEOUT_MS = 2000
        private val HDHR_BROADCAST_MAGIC = byteArrayOf(0x04.toByte(), 0x00, 0x00.toByte(), 0x01.toByte())
    }

    override val backendName: String = TunerBackendType.NETWORK_HDHR.name

    private var selectedDevice: TunerDevice? = null
    private var activeStreamUrl: String? = null

    // ── Discovery ─────────────────────────────────────────────────────────────

    override suspend fun discoverTuners(): List<TunerDevice> = withContext(Dispatchers.IO) {
        val discovered = mutableListOf<TunerDevice>()
        try {
            // Simple UDP broadcast discovery — HDHomeRun responds with its IP
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = HDHR_DISCOVER_TIMEOUT_MS
                val broadcastAddr = InetAddress.getByName("255.255.255.255")
                val packet = DatagramPacket(
                    HDHR_BROADCAST_MAGIC,
                    HDHR_BROADCAST_MAGIC.size,
                    broadcastAddr,
                    HDHR_DISCOVER_PORT,
                )
                socket.send(packet)
                val buf = ByteArray(1024)
                val response = DatagramPacket(buf, buf.size)
                try {
                    while (true) {
                        socket.receive(response)
                        val ip = response.address.hostAddress ?: continue
                        val info = fetchDiscoverJson(ip)
                        if (info != null) {
                            discovered += TunerDevice(
                                id = "hdhr-${info.deviceId}",
                                displayName = "HDHomeRun ${info.modelNumber} (${ip})",
                                backendType = TunerBackendType.NETWORK_HDHR,
                                address = ip,
                                isConnected = true,
                            )
                        }
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    // Expected — no more responses
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "UDP discovery error (may be normal on some networks): ${e.message}")
        }

        // Fallback: try common IP ranges if broadcast failed — skip for now;
        // user can manually enter IP in settings.
        Log.d(TAG, "discoverTuners: found ${discovered.size} HDHomeRun device(s)")
        discovered
    }

    override suspend fun requestPermission(device: TunerDevice): TunerResult<Unit> {
        // Network tuner needs no Android permission beyond INTERNET (declared in manifest)
        return TunerResult.Success(Unit)
    }

    override suspend fun openTuner(device: TunerDevice): TunerResult<Unit> {
        selectedDevice = device
        Log.i(TAG, "openTuner: ${device.displayName}")
        return TunerResult.Success(Unit)
    }

    override suspend fun closeTuner() {
        activeStreamUrl = null
        selectedDevice = null
        Log.i(TAG, "closeTuner")
    }

    override suspend fun tune(request: TuneRequest): TunerResult<Unit> {
        val device = selectedDevice
            ?: return TunerResult.Failure(TunerError.DeviceNotFound("No HDHomeRun device selected"))

        // HDHomeRun stream URL format: http://<ip>/auto/v<freq_hz> or /auto/v<program>
        // Using frequency: http://<ip>/auto/v<freq_khz*1000>?transcode=none
        val freqHz = request.rfChannelKhz.toLong() * 1000L
        activeStreamUrl = "http://${device.address}/auto/v${freqHz}"
        Log.d(TAG, "tune: $activeStreamUrl (program=${request.programNumber})")
        return TunerResult.Success(Unit)
    }

    override suspend fun stopStream() {
        activeStreamUrl = null
    }

    /**
     * Returns the HTTP stream URL as a single-item flow.
     * The caller (PlaybackEngine) uses this URL with Media3's HttpDataSource.
     * For raw TS data we emit an empty flow — ExoPlayer handles HTTP streaming directly.
     */
    override fun readTransportStream(): Flow<ByteArray> = emptyFlow()

    /**
     * Returns the current active stream URL for use by the playback engine.
     * This is an HDHomeRun-specific extension beyond the base [TunerBackend] interface.
     */
    fun getStreamUrl(): String? = activeStreamUrl

    override fun scanChannels(mode: ScanMode): Flow<ScanEvent> = callbackFlow {
        val device = selectedDevice ?: run {
            send(ScanEvent.Error(TunerError.DeviceNotFound("No device selected")))
            close()
            return@callbackFlow
        }
        // HDHomeRun lineup endpoint provides all available channels without scanning
        withContext(Dispatchers.IO) {
            try {
                val lineupJson = fetchLineupJson(device.address)
                var count = 0
                lineupJson?.forEach { entry ->
                    send(
                        ScanEvent.ChannelFound(
                            rfChannelKhz = entry.rfChannelKhz,
                            programNumber = entry.programNumber,
                            majorChannel = entry.majorChannel,
                            minorChannel = entry.minorChannel,
                            callsign = entry.guideName,
                            serviceName = entry.guideName,
                            isEncrypted = entry.drm,
                        )
                    )
                    count++
                }
                send(ScanEvent.Progress(0, 100))
                send(ScanEvent.Complete)
                Log.i(TAG, "scanChannels: found $count channels from lineup")
            } catch (e: Exception) {
                Log.e(TAG, "scanChannels error", e)
                send(ScanEvent.Error(TunerError.Unknown("Lineup fetch failed: ${e.message}", e)))
            }
        }
        awaitClose()
    }

    override suspend fun getSignalMetrics(): SignalMetrics = withContext(Dispatchers.IO) {
        val device = selectedDevice ?: return@withContext noSignal()
        try {
            val json = fetchUrl("http://${device.address}/status.json")
            // Very simplified parse — a full JSON parser would be used in production
            val snr = extractFloat(json, "\"SNR\":", ",")
            val strength = extractFloat(json, "\"SignalStrength\":", ",")
            val quality = extractFloat(json, "\"SignalQuality\":", ",")
            val locked = json.contains("\"Lock\":\"8vsb\"") || json.contains("\"Lock\":\"atsc3\"")
            SignalMetrics(
                snrDb = snr,
                qualityPercent = quality?.toInt(),
                strengthDbm = strength,
                isLocked = locked,
            )
        } catch (e: Exception) {
            Log.w(TAG, "getSignalMetrics failed: ${e.message}")
            noSignal()
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private data class DiscoverInfo(val deviceId: String, val modelNumber: String, val ip: String)
    private data class LineupEntry(
        val guideName: String,
        val rfChannelKhz: Int,
        val programNumber: Int,
        val majorChannel: Int,
        val minorChannel: Int,
        val drm: Boolean,
    )

    private fun fetchDiscoverJson(ip: String): DiscoverInfo? = try {
        val json = fetchUrl("http://$ip/discover.json")
        DiscoverInfo(
            deviceId = extractString(json, "\"DeviceID\":\"", "\"") ?: ip,
            modelNumber = extractString(json, "\"ModelNumber\":\"", "\"") ?: "Unknown",
            ip = ip,
        )
    } catch (_: Exception) { null }

    private fun fetchLineupJson(ip: String): List<LineupEntry>? = try {
        val json = fetchUrl("http://$ip/lineup.json")
        parseLineup(json)
    } catch (_: Exception) { null }

    /**
     * Minimal string-based JSON parser for the HDHomeRun lineup response.
     * Replace with a real JSON library (Moshi/kotlinx.serialization) for production.
     */
    private fun parseLineup(json: String): List<LineupEntry> {
        val entries = mutableListOf<LineupEntry>()
        // Each channel is a JSON object in the array: {"GuideNumber":"7.1","GuideName":"KPIX",...}
        val objectRegex = Regex("\\{[^}]+\\}")
        objectRegex.findAll(json).forEach { match ->
            val obj = match.value
            val guideName = extractString(obj, "\"GuideName\":\"", "\"") ?: return@forEach
            val guideNumber = extractString(obj, "\"GuideNumber\":\"", "\"") ?: "0.0"
            val parts = guideNumber.split(".")
            val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val url = extractString(obj, "\"URL\":\"", "\"") ?: ""
            val drm = obj.contains("\"DRM\":1")
            // RF channel from URL: .../auto/v<freq_hz>
            val freqHz = Regex("auto/v(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            entries += LineupEntry(
                guideName = guideName,
                rfChannelKhz = (freqHz / 1000).toInt(),
                programNumber = 0,
                majorChannel = major,
                minorChannel = minor,
                drm = drm,
            )
        }
        return entries
    }

    private fun fetchUrl(urlString: String): String {
        val conn = URL(urlString).openConnection()
        conn.connectTimeout = 3000
        conn.readTimeout = 5000
        return BufferedReader(InputStreamReader(conn.getInputStream())).use { it.readText() }
    }

    private fun extractString(src: String, after: String, before: String): String? {
        val start = src.indexOf(after)
        if (start < 0) return null
        val valueStart = start + after.length
        val end = src.indexOf(before, valueStart)
        if (end < 0) return null
        return src.substring(valueStart, end)
    }

    private fun extractFloat(src: String, after: String, before: String): Float? =
        extractString(src, after, before)?.trim()?.toFloatOrNull()

    private fun noSignal() = SignalMetrics(null, null, null, false)
}
