package dev.tvtuner.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.tvtuner.core.data.db.dao.ChannelDao
import dev.tvtuner.core.data.db.dao.GuideDao
import dev.tvtuner.core.data.db.dao.RecordingDao
import dev.tvtuner.core.data.db.dao.ScheduledRecordingDao
import dev.tvtuner.core.data.db.entity.ChannelEntity
import dev.tvtuner.core.data.db.entity.GuideEntryEntity
import dev.tvtuner.core.data.db.entity.RecordingEntity
import dev.tvtuner.core.data.db.entity.ScheduledRecordingEntity

@Database(
    entities = [
        ChannelEntity::class,
        GuideEntryEntity::class,
        RecordingEntity::class,
        ScheduledRecordingEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class TvTunerDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun guideDao(): GuideDao
    abstract fun recordingDao(): RecordingDao
    abstract fun scheduledRecordingDao(): ScheduledRecordingDao

    companion object {
        const val DATABASE_NAME = "tvtuner.db"
    }
}
