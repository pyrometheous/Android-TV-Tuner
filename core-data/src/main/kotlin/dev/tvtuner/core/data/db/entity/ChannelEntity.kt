package dev.tvtuner.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single television service (channel) discovered via a tuner scan.
 */
@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** RF/physical channel frequency in kHz */
    @ColumnInfo(name = "rf_channel_khz")
    val rfChannelKhz: Int,

    /** Major virtual channel number (e.g. 7 for 7.1) */
    @ColumnInfo(name = "major_channel")
    val majorChannel: Int,

    /** Minor virtual channel number (e.g. 1 for 7.1) */
    @ColumnInfo(name = "minor_channel")
    val minorChannel: Int,

    /** PSIP short name / callsign, e.g. "KPIX" */
    @ColumnInfo(name = "callsign")
    val callsign: String,

    /** Full service name from PSIP, e.g. "CBS HD" */
    @ColumnInfo(name = "service_name")
    val serviceName: String,

    /** MPEG-2 program number within the transport stream */
    @ColumnInfo(name = "program_number")
    val programNumber: Int,

    /** Transport stream ID */
    @ColumnInfo(name = "transport_stream_id")
    val transportStreamId: Int = 0,

    /** Whether this channel is CA-encrypted and cannot be decoded */
    @ColumnInfo(name = "is_encrypted")
    val isEncrypted: Boolean = false,

    /** Whether the user has hidden this channel */
    @ColumnInfo(name = "is_hidden")
    val isHidden: Boolean = false,

    /** Whether the user has favorited this channel */
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,

    /** User-supplied display name override; null means use serviceName */
    @ColumnInfo(name = "user_name_override")
    val userNameOverride: String? = null,

    /** Epoch millis of the last successful metadata refresh */
    @ColumnInfo(name = "last_metadata_refresh_ms")
    val lastMetadataRefreshMs: Long = 0,

    /** Guide metadata completeness: FULL, PARTIAL, NONE */
    @ColumnInfo(name = "guide_status")
    val guideStatus: String = GuideStatus.NONE,

    /** User-defined sort order override; null = natural order */
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int? = null,
) {
    val displayName: String get() = userNameOverride ?: serviceName.ifBlank { callsign }
    val virtualChannelDisplay: String get() = "$majorChannel.$minorChannel"
}

object GuideStatus {
    const val FULL = "FULL"
    const val PARTIAL = "PARTIAL"
    const val NONE = "NONE"
}
