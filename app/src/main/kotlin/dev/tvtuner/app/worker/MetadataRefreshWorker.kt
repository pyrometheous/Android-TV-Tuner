package dev.tvtuner.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.tvtuner.core.data.db.entity.GuideEntryEntity
import dev.tvtuner.core.data.repository.ChannelRepository
import dev.tvtuner.core.data.repository.GuideRepository
import dev.tvtuner.parser.atsc.PsipEvent
import dev.tvtuner.parser.atsc.PsipProcessor
import dev.tvtuner.tuner.core.TuneRequest
import dev.tvtuner.tuner.core.TunerManager
import dev.tvtuner.tuner.core.TunerResult
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that enriches guide data from the live broadcast signal.
 *
 * For each channel in the database, the worker briefly tunes to it and collects
 * PSIP sections for up to [DWELL_PER_CHANNEL_MS] milliseconds. Parsed EIT events
 * are persisted to [GuideRepository]; channel metadata is updated via
 * [ChannelRepository.markGuideRefreshed].
 *
 * This worker runs entirely offline — no network calls are made.
 */
@HiltWorker
class MetadataRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val tunerManager: TunerManager,
    private val channelRepository: ChannelRepository,
    private val guideRepository: GuideRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val channels = channelRepository.getAll()
        if (channels.isEmpty()) return Result.success()

        val processor = PsipProcessor()

        for (channel in channels) {
            val tuneResult = tunerManager.tune(
                TuneRequest(
                    rfChannelKhz = channel.rfChannelKhz,
                    programNumber = channel.programNumber,
                )
            )
            if (tuneResult is TunerResult.Failure) continue

            val guideEntries = mutableListOf<GuideEntryEntity>()

            withTimeoutOrNull(DWELL_PER_CHANNEL_MS) {
                tunerManager.readTransportStream()
                    .collect { rawPacket ->
                        val events = processor.process(rawPacket)
                        for (event in events) {
                            if (event is PsipEvent.EitParsed) {
                                event.eit.events.mapTo(guideEntries) { eitEvent ->
                                    GuideEntryEntity(
                                        channelId = channel.id,
                                        title = eitEvent.title,
                                        subtitle = "",
                                        description = "",
                                        startTimeMs = eitEvent.startTimeMs,
                                        durationMs = eitEvent.durationSeconds * 1_000L,
                                        rating = eitEvent.rating ?: "",
                                        source = "PSIP",
                                    )
                                }
                            }
                        }
                    }
            }

            if (guideEntries.isNotEmpty()) {
                guideRepository.insertEntries(guideEntries)
                channelRepository.markGuideRefreshed(channel.id)
            }

            tunerManager.stopStream()
        }

        return Result.success()
    }

    companion object {
        private const val DWELL_PER_CHANNEL_MS = 10_000L
        private const val UNIQUE_WORK_NAME = "metadata_refresh"

        /** Enqueue a one-time immediate metadata refresh. */
        fun enqueueOneTime(context: Context) {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<MetadataRefreshWorker>().build()
            )
        }

        /** Schedule a periodic refresh every 4 hours. */
        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<MetadataRefreshWorker>(4, TimeUnit.HOURS).build(),
            )
        }
    }
}


/**
 * WorkManager worker that enriches guide data from the live broadcast signal.
 *
 * For each channel in the database, the worker briefly tunes to it and collects
 * PSIP sections for up to [DWELL_PER_CHANNEL_SECONDS] seconds. Parsed EIT events
 * are persisted to [GuideRepository]; updated channel metadata is saved via
 * [ChannelRepository.markGuideRefreshed].
 *
 * This worker runs entirely offline — no network calls are made.
 */
@HiltWorker
class MetadataRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val tunerManager: TunerManager,
    private val channelRepository: ChannelRepository,
    private val guideRepository: GuideRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val channels = channelRepository.getAll()
        if (channels.isEmpty()) return Result.success()

        val processor = PsipProcessor()

        for (channel in channels) {
            // Tune to the RF channel
            val tuneResult = tunerManager.tune(
                dev.tvtuner.tuner.core.TuneRequest(
                    rfChannelKhz = channel.rfChannelKhz,
                    programNumber = channel.programNumber,
                )
            )
            if (tuneResult is dev.tvtuner.tuner.core.TunerResult.Failure) continue

            // Collect TS data for up to DWELL_PER_CHANNEL_SECONDS
            withTimeoutOrNull(DWELL_PER_CHANNEL_SECONDS.seconds) {
                tunerManager.readTransportStream()
                    .catch { /* stream errors are non-fatal; move to next channel */ }
                    .collect { packet ->
                        processor.process(packet)
                    }
            }

            // Persist any PSIP events emitted during this dwell
            val events = processor.drainEvents()
            for (event in events) {
                when (event) {
                    is PsipEvent.EitParsed -> {
                        val entries = event.eit.events.map { eitEvent ->
                            dev.tvtuner.core.data.db.entity.GuideEntryEntity(
                                channelId = channel.id,
                                title = eitEvent.title,
                                subtitle = "",
                                description = eitEvent.description,
                                startTimeMs = eitEvent.startTimeMs,
                                durationMs = eitEvent.durationMs,
                                rating = eitEvent.etv ?: "",
                                source = "PSIP",
                            )
                        }
                        if (entries.isNotEmpty()) {
                            guideRepository.insertEntries(entries)
                            channelRepository.markGuideRefreshed(channel.id)
                        }
                    }
                    is PsipEvent.VctParsed -> { /* channel metadata update handled at scan time */ }
                }
            }

            tunerManager.stopStream()
        }

        return Result.success()
    }

    companion object {
        private const val DWELL_PER_CHANNEL_SECONDS = 10L
        private const val UNIQUE_WORK_NAME = "metadata_refresh"

        /** Enqueue a one-time immediate metadata refresh. */
        fun enqueueOneTime(context: Context) {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<MetadataRefreshWorker>().build()
            )
        }

        /** Schedule a periodic refresh every 4 hours. */
        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<MetadataRefreshWorker>(4, TimeUnit.HOURS).build(),
            )
        }
    }
}
