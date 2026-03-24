package dev.tvtuner.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A recorded programme stored on device storage. */
@Entity(
    tableName = "recordings",
    foreignKeys = [
        ForeignKey(
            entity = ChannelEntity::class,
            parentColumns = ["id"],
            childColumns = ["channel_id"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    indices = [Index(value = ["channel_id"])],
)
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "channel_id")
    val channelId: Long? = null,

    @ColumnInfo(name = "channel_display_name")
    val channelDisplayName: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "subtitle")
    val subtitle: String? = null,

    @ColumnInfo(name = "description")
    val description: String? = null,

    /** Epoch millis when recording started */
    @ColumnInfo(name = "start_time_ms")
    val startTimeMs: Long,

    /** Epoch millis when recording ended; null if still recording */
    @ColumnInfo(name = "end_time_ms")
    val endTimeMs: Long? = null,

    /** Duration as recorded in milliseconds */
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long = 0,

    /** Absolute path on device filesystem */
    @ColumnInfo(name = "file_path")
    val filePath: String,

    /** File size in bytes; 0 if not yet known */
    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long = 0,

    /** Thumbnail image file path; null if not yet generated */
    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String? = null,

    /** Resume position in milliseconds */
    @ColumnInfo(name = "watched_position_ms")
    val watchedPositionMs: Long = 0,

    /** Whether playback has been fully watched */
    @ColumnInfo(name = "is_watched")
    val isWatched: Boolean = false,

    /** User-locked; will not be auto-deleted by storage management */
    @ColumnInfo(name = "is_protected")
    val isProtected: Boolean = false,

    /** RecordingStatus enum string */
    @ColumnInfo(name = "status")
    val status: String = RecordingStatus.COMPLETE,
)

object RecordingStatus {
    const val RECORDING = "RECORDING"
    const val COMPLETE = "COMPLETE"
    const val FAILED = "FAILED"
    const val INTERRUPTED = "INTERRUPTED"
}
