package dev.tvtuner.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A guide-based scheduled recording rule. */
@Entity(
    tableName = "scheduled_recordings",
    foreignKeys = [
        ForeignKey(
            entity = ChannelEntity::class,
            parentColumns = ["id"],
            childColumns = ["channel_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["channel_id"])],
)
data class ScheduledRecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "channel_id")
    val channelId: Long,

    /** Title to match; used for "record every airing" mode */
    @ColumnInfo(name = "title_match")
    val titleMatch: String,

    /** Corresponding guide entry id if this is a one-shot recording */
    @ColumnInfo(name = "guide_entry_id")
    val guideEntryId: Long? = null,

    /** Epoch millis of scheduled start */
    @ColumnInfo(name = "scheduled_start_ms")
    val scheduledStartMs: Long,

    /** Epoch millis of scheduled end */
    @ColumnInfo(name = "scheduled_end_ms")
    val scheduledEndMs: Long,

    /** SINGLE or SERIES */
    @ColumnInfo(name = "repeat_mode")
    val repeatMode: String = RepeatMode.SINGLE,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
)

object RepeatMode {
    const val SINGLE = "SINGLE"
    const val SERIES = "SERIES"
}
