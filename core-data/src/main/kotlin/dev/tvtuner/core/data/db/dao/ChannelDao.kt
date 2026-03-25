package dev.tvtuner.core.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.tvtuner.core.data.db.entity.ChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {

    @Query("SELECT * FROM channels WHERE is_hidden = 0 ORDER BY major_channel ASC, minor_channel ASC")
    fun observeVisibleChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels ORDER BY major_channel ASC, minor_channel ASC")
    fun observeAllChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE is_favorite = 1 AND is_hidden = 0 ORDER BY major_channel ASC, minor_channel ASC")
    fun observeFavorites(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun getById(id: Long): ChannelEntity?

    @Query("SELECT * FROM channels WHERE rf_channel_khz = :rfKhz AND program_number = :programNumber LIMIT 1")
    suspend fun findByRfAndProgram(rfKhz: Int, programNumber: Int): ChannelEntity?

    @Query("SELECT * FROM channels WHERE major_channel = :major AND minor_channel = :minor AND is_hidden = 0 LIMIT 1")
    suspend fun findByMajorMinor(major: Int, minor: Int): ChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<ChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(channel: ChannelEntity): Long

    @Update
    suspend fun update(channel: ChannelEntity)

    @Delete
    suspend fun delete(channel: ChannelEntity)

    @Query("DELETE FROM channels")
    suspend fun deleteAll()

    @Query("UPDATE channels SET is_favorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE channels SET is_hidden = :isHidden WHERE id = :id")
    suspend fun setHidden(id: Long, isHidden: Boolean)

    @Query("UPDATE channels SET user_name_override = :name WHERE id = :id")
    suspend fun setUserName(id: Long, name: String?)

    @Query(
        """UPDATE channels SET
            guide_status = :status,
            last_metadata_refresh_ms = :timestampMs
           WHERE id = :id"""
    )
    suspend fun updateGuideStatus(id: Long, status: String, timestampMs: Long)

    @Query("SELECT COUNT(*) FROM channels")
    suspend fun count(): Int

    @Query("SELECT * FROM channels ORDER BY major_channel ASC, minor_channel ASC")
    suspend fun getAll(): List<ChannelEntity>
}
