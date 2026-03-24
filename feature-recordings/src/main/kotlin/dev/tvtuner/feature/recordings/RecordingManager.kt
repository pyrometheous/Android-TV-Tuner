package dev.tvtuner.feature.recordings

import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tvtuner.core.data.db.entity.ChannelEntity
import dev.tvtuner.core.data.db.entity.RecordingEntity
import dev.tvtuner.core.data.db.entity.RecordingStatus
import dev.tvtuner.core.data.preferences.AppPreferences
import dev.tvtuner.core.data.repository.RecordingRepository
import kotlinx.coroutines.flow.first
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recordingRepository: RecordingRepository,
    private val prefs: AppPreferences,
) {
    companion object {
        private const val TAG = "RecordingManager"
    }

    suspend fun startRecording(channel: ChannelEntity, title: String): Long {
        val storageDir = prefs.recordingStoragePath.first()
            ?: context.getExternalFilesDir("Recordings")?.absolutePath
            ?: context.filesDir.absolutePath + "/Recordings"

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "${channel.callsign}_${timestamp}.ts"
        val filePath = "$storageDir/$fileName"

        File(storageDir).mkdirs()

        val entity = RecordingEntity(
            channelId = channel.id,
            channelDisplayName = channel.displayName,
            title = title,
            startTimeMs = System.currentTimeMillis(),
            filePath = filePath,
            status = RecordingStatus.RECORDING,
        )
        val recordingId = recordingRepository.insert(entity)
        Log.i(TAG, "Recording started: id=$recordingId path=$filePath")

        val serviceIntent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
            putExtra(RecordingService.EXTRA_RECORDING_ID, recordingId)
            putExtra(RecordingService.EXTRA_CHANNEL_ID, channel.id)
        }
        context.startForegroundService(serviceIntent)
        return recordingId
    }

    fun stopRecording() {
        val stopIntent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP
        }
        context.startService(stopIntent)
        Log.i(TAG, "Requested recording stop")
    }

    suspend fun deleteRecording(id: Long) {
        val recording = recordingRepository.getById(id) ?: return
        File(recording.filePath).delete()
        recordingRepository.deleteById(id)
    }
}
