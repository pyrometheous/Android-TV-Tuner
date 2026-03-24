package dev.tvtuner.core.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.tvtuner.core.data.db.entity.ScheduledRecordingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledRecordingDao {

    @Query("SELECT * FROM scheduled_recordings WHERE is_active = 1 ORDER BY scheduled_start_ms ASC")
    fun observeActive(): Flow<List<ScheduledRecordingEntity>>

    @Query("SELECT * FROM scheduled_recordings WHERE scheduled_start_ms <= :nowMs AND is_active = 1")
    suspend fun getDue(nowMs: Long): List<ScheduledRecordingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(schedule: ScheduledRecordingEntity): Long

    @Update
    suspend fun update(schedule: ScheduledRecordingEntity)

    @Delete
    suspend fun delete(schedule: ScheduledRecordingEntity)
}
