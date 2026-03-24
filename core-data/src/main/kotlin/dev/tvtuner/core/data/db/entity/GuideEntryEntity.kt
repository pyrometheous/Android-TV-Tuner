package dev.tvtuner.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single program/event entry from the PSIP Event Information Table (EIT).
 */
@Entity(
    tableName = "guide_entries",
    foreignKeys = [
        ForeignKey(
            entity = ChannelEntity::class,
            parentColumns = ["id"],
            childColumns = ["channel_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["channel_id", "start_time_ms"]),
        Index(value = ["channel_id"]),
    ],
)
data class GuideEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "channel_id")
    val channelId: Long,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "subtitle")
    val subtitle: String? = null,

    @ColumnInfo(name = "description")
    val description: String? = null,

    /** Epoch millis */
    @ColumnInfo(name = "start_time_ms")
    val startTimeMs: Long,

    /** Duration in milliseconds */
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,

    /** PSIP content advisory rating string, e.g. "TV-PG" */
    @ColumnInfo(name = "rating")
    val rating: String? = null,

    /** Source: "eit", "stt_estimate", etc. */
    @ColumnInfo(name = "source")
    val source: String = "eit",
) {
    val endTimeMs: Long get() = startTimeMs + durationMs
}
