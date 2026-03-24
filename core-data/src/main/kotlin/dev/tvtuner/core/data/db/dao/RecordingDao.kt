package dev.tvtuner.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.tvtuner.core.data.db.entity.RecordingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    @Query("SELECT * FROM recordings ORDER BY start_time_ms DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE status = 'RECORDING' ORDER BY start_time_ms DESC")
    fun observeActive(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: Long): RecordingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recording: RecordingEntity): Long

    @Update
    suspend fun update(recording: RecordingEntity)

    @Query("UPDATE recordings SET watched_position_ms = :posMs, is_watched = :isWatched WHERE id = :id")
    suspend fun updateWatchedProgress(id: Long, posMs: Long, isWatched: Boolean)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT SUM(file_size_bytes) FROM recordings")
    suspend fun totalStorageBytes(): Long?
}
