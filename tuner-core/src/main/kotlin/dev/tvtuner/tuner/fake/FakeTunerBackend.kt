package dev.tvtuner.tuner.fake

import dev.tvtuner.tuner.core.ScanEvent
import dev.tvtuner.tuner.core.ScanMode
import dev.tvtuner.tuner.core.SignalMetrics
import dev.tvtuner.tuner.core.TuneRequest
import dev.tvtuner.tuner.core.TunerBackend
import dev.tvtuner.tuner.core.TunerBackendType
import dev.tvtuner.tuner.core.TunerDevice
import dev.tvtuner.tuner.core.TunerError
import dev.tvtuner.tuner.core.TunerResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * *** FAKE TUNER BACKEND — FOR DEVELOPMENT AND PREVIEW ONLY ***
 *
 * Simulates a tuner device with a realistic set of demo channels.
 * Never reports real signal. Never returns real transport stream data.
 * Clearly identified in all log output and UI.
 */
class FakeTunerBackend @Inject constructor() : TunerBackend {

    override val backendName: String = TunerBackendType.FAKE.name

    private val fakeDevice = TunerDevice(
        id = "fake-001",
        displayName = "Demo Tuner (Fake Data)",
        backendType = TunerBackendType.FAKE,
        address = "FAKE",
        isConnected = true,
    )

    override suspend fun discoverTuners(): List<TunerDevice> = listOf(fakeDevice)

    override suspend fun requestPermission(device: TunerDevice): TunerResult<Unit> =
        TunerResult.Success(Unit)

    override suspend fun openTuner(device: TunerDevice): TunerResult<Unit> =
        TunerResult.Success(Unit)

    override suspend fun closeTuner() = Unit

    override suspend fun tune(request: TuneRequest): TunerResult<Unit> =
        TunerResult.Success(Unit)

    override suspend fun stopStream() = Unit

    /** Fake backend provides no real TS data — playback uses ExoPlayer with a fake URI. */
    override fun readTransportStream(): Flow<ByteArray> = emptyFlow()

    override fun scanChannels(mode: ScanMode): Flow<ScanEvent> = flow {
        val demoChannels = buildDemoChannels()
        val total = US_ATSC_FREQUENCIES.size
        US_ATSC_FREQUENCIES.forEachIndexed { index, freqKhz ->
            delay(80) // simulate tune + lock time
            emit(ScanEvent.Progress(freqKhz, (index + 1) * 100 / total))
            demoChannels[freqKhz]?.forEach { emit(it) }
        }
        delay(200)
        emit(ScanEvent.Complete)
    }

    override suspend fun getSignalMetrics(): SignalMetrics = SignalMetrics(
        snrDb = 28.5f,
        qualityPercent = 95,
        strengthDbm = -55.0f,
        isLocked = true,
    )

    // ── Demo data ────────────────────────────────────────────────────────────

    private fun buildDemoChannels(): Map<Int, List<ScanEvent.ChannelFound>> = mapOf(
        177000 to listOf(
            ScanEvent.ChannelFound(177000, 1, 2, 1, "KTVU", "KTVU FOX 2", false),
            ScanEvent.ChannelFound(177000, 2, 2, 2, "KTVU2", "KTVU2 ME TV", false),
        ),
        189000 to listOf(
            ScanEvent.ChannelFound(189000, 1, 4, 1, "KRON", "KRON 4 News", false),
        ),
        203000 to listOf(
            ScanEvent.ChannelFound(203000, 1, 5, 1, "KPIX", "KPIX CBS 5", false),
            ScanEvent.ChannelFound(203000, 2, 5, 2, "KPIX2", "CW 44", false),
        ),
        209000 to listOf(
            ScanEvent.ChannelFound(209000, 1, 7, 1, "KGO", "KGO ABC 7", false),
            ScanEvent.ChannelFound(209000, 2, 7, 2, "KGO2", "Laff Network", false),
        ),
        593000 to listOf(
            ScanEvent.ChannelFound(593000, 1, 11, 1, "KNTV", "NBC Bay Area 11", false),
            ScanEvent.ChannelFound(593000, 2, 11, 2, "KNTV2", "Cozi TV", false),
        ),
        653000 to listOf(
            ScanEvent.ChannelFound(653000, 1, 14, 1, "KDTV", "Univision 14", false),
        ),
    )

    companion object {
        /** Subset of US ATSC broadcast frequencies to simulate a scan (6 MHz channels) */
        private val US_ATSC_FREQUENCIES = (57..803 step 6).map { it * 1000 }.take(24)
    }
}
