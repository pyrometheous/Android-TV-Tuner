package dev.tvtuner.feature.recordings

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.tvtuner.core.data.db.entity.ChannelEntity
import dev.tvtuner.core.data.db.entity.RecordingEntity
import dev.tvtuner.core.data.db.entity.RecordingStatus
import dev.tvtuner.core.data.repository.RecordingRepository
import dev.tvtuner.tuner.core.TunerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * Foreground service that manages active recordings.
 *
 * Transport stream capture is written to the configured storage path.
 * The service remains active for the duration of the recording.
 *
 * NOTE: Actual TS capture requires a working TunerBackend.readTransportStream().
 * For USB backends this is not yet implemented. See DEVELOPMENT_STATUS.md.
 */
@AndroidEntryPoint
class RecordingService : Service() {

    companion object {
        private const val TAG = "RecordingService"
        const val ACTION_START = "dev.tvtuner.ACTION_START_RECORDING"
        const val ACTION_STOP = "dev.tvtuner.ACTION_STOP_RECORDING"
        const val EXTRA_CHANNEL_ID = "channel_id"
        const val EXTRA_RECORDING_ID = "recording_id"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "recording_channel"
    }

    @Inject lateinit var tunerManager: TunerManager
    @Inject lateinit var recordingRepository: RecordingRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeRecordingId: Long = -1L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val recordingId = intent.getLongExtra(EXTRA_RECORDING_ID, -1L)
                if (recordingId != -1L) {
                    startRecording(recordingId)
                }
            }
            ACTION_STOP -> {
                stopRecording()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startRecording(recordingId: Long) {
        activeRecordingId = recordingId
        startForeground(NOTIFICATION_ID, buildNotification("Recording…"))
        Log.i(TAG, "Started recording id=$recordingId")

        serviceScope.launch {
            val recording = recordingRepository.getById(recordingId) ?: return@launch
            val outputFile = File(recording.filePath)
            outputFile.parentFile?.mkdirs()

            try {
                FileOutputStream(outputFile).use { out ->
                    tunerManager.readTransportStream().collect { tsPacket ->
                        out.write(tsPacket)
                        // Update file size periodically (every ~10 MB)
                        if (outputFile.length() % (10 * 1024 * 1024) < 188) {
                            recordingRepository.update(
                                recording.copy(fileSizeBytes = outputFile.length())
                            )
                        }
                    }
                }
                recordingRepository.update(
                    recording.copy(
                        status = RecordingStatus.COMPLETE,
                        endTimeMs = System.currentTimeMillis(),
                        durationMs = System.currentTimeMillis() - recording.startTimeMs,
                        fileSizeBytes = outputFile.length(),
                    )
                )
                Log.i(TAG, "Recording complete: ${recording.filePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Recording failed", e)
                recordingRepository.update(
                    recording.copy(
                        status = RecordingStatus.FAILED,
                        endTimeMs = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

    private fun stopRecording() {
        val id = activeRecordingId
        if (id == -1L) return
        serviceScope.launch {
            val recording = recordingRepository.getById(id) ?: return@launch
            if (recording.status == RecordingStatus.RECORDING) {
                recordingRepository.update(
                    recording.copy(
                        status = RecordingStatus.COMPLETE,
                        endTimeMs = System.currentTimeMillis(),
                        durationMs = System.currentTimeMillis() - recording.startTimeMs,
                    )
                )
            }
        }
        activeRecordingId = -1L
        Log.i(TAG, "Recording stopped")
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TV Recording")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "TV Recordings",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Active recording notifications"
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }
}
