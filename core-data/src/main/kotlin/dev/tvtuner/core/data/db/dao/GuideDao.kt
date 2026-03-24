package dev.tvtuner.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.tvtuner.core.data.db.entity.GuideEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GuideDao {

    @Query(
        """SELECT * FROM guide_entries
           WHERE channel_id = :channelId
             AND (start_time_ms + duration_ms) > :nowMs
           ORDER BY start_time_ms ASC"""
    )
    fun observeUpcomingForChannel(channelId: Long, nowMs: Long): Flow<List<GuideEntryEntity>>

    @Query(
        """SELECT * FROM guide_entries
           WHERE channel_id IN (:channelIds)
             AND (start_time_ms + duration_ms) > :fromMs
             AND start_time_ms < :toMs
           ORDER BY channel_id ASC, start_time_ms ASC"""
    )
    fun observeGuideWindow(
        channelIds: List<Long>,
        fromMs: Long,
        toMs: Long,
    ): Flow<List<GuideEntryEntity>>

    @Query(
        """SELECT * FROM guide_entries
           WHERE channel_id = :channelId
             AND start_time_ms <= :nowMs
             AND (start_time_ms + duration_ms) > :nowMs
           LIMIT 1"""
    )
    suspend fun getCurrentProgram(channelId: Long, nowMs: Long): GuideEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<GuideEntryEntity>)

    @Query("DELETE FROM guide_entries WHERE channel_id = :channelId")
    suspend fun deleteForChannel(channelId: Long)

    @Query("DELETE FROM guide_entries WHERE (start_time_ms + duration_ms) < :beforeMs")
    suspend fun deleteExpired(beforeMs: Long)
}
