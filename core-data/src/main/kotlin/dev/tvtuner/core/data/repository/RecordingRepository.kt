package dev.tvtuner.core.data.repository

import dev.tvtuner.core.data.db.dao.RecordingDao
import dev.tvtuner.core.data.db.entity.RecordingEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingRepository @Inject constructor(
    private val recordingDao: RecordingDao,
) {
    fun observeAll(): Flow<List<RecordingEntity>> = recordingDao.observeAll()

    fun observeActive(): Flow<List<RecordingEntity>> = recordingDao.observeActive()

    suspend fun getById(id: Long): RecordingEntity? = recordingDao.getById(id)

    suspend fun insert(recording: RecordingEntity): Long = recordingDao.insert(recording)

    suspend fun update(recording: RecordingEntity) = recordingDao.update(recording)

    suspend fun updateWatchedProgress(id: Long, posMs: Long, isWatched: Boolean) =
        recordingDao.updateWatchedProgress(id, posMs, isWatched)

    suspend fun deleteById(id: Long) = recordingDao.deleteById(id)

    suspend fun totalStorageUsedBytes(): Long = recordingDao.totalStorageBytes() ?: 0L
}
