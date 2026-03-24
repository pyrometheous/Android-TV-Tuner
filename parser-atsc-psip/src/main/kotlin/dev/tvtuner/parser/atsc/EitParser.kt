package dev.tvtuner.parser.atsc

/**
 * Parsed PSIP Event Information Table (EIT) entries.
 * Reference: ATSC A/65:2013 §6.5
 *
 * ATSC time: seconds since midnight UTC January 6, 1980 (GPS epoch).
 * GPS offset from Unix epoch: 315964800 seconds.
 * Leap second delta (as of 2024): 18 seconds.
 */
data class ParsedEit(
    val sourceId: Int,
    val events: List<EitEvent>,
)

data class EitEvent(
    val eventId: Int,
    val startTimeGps: Long,     // GPS seconds
    val startTimeMs: Long,      // Unix epoch millis
    val durationSeconds: Int,
    val title: String,
    val rating: String?,
)

object EitParser {

    // GPS epoch offset: Jan 6 1980 in Unix seconds
    private const val GPS_EPOCH_UNIX_SEC = 315964800L
    // As of 2024, GPS is 18 seconds ahead of UTC (leap seconds)
    private const val GPS_LEAP_SECONDS = 18L

    /**
     * Parse an EIT section.
     */
    fun parse(section: ByteArray): ParsedEit? {
        if (section.size < 10) return null
        val tableId = section[0].toInt() and 0xFF
        if (tableId < TsConstants.TABLE_ID_EIT_0 || tableId > TsConstants.TABLE_ID_EIT_3) return null

        val sourceId = readUInt16(section, 3)
        val numEventsInSection = section[9].toInt() and 0xFF
        val events = mutableListOf<EitEvent>()
        var offset = 10

        repeat(numEventsInSection) {
            if (offset + 10 > section.size) return@repeat

            val b0 = section[offset].toInt() and 0xFF
            val b1 = section[offset + 1].toInt() and 0xFF
            val eventId = ((b0 and 0x3F) shl 8) or b1

            val gpsTime = readUInt32(section, offset + 2)
            val unixMs = gpsToUnixMs(gpsTime)

            val durationSec = readUInt24(section, offset + 6)

            val b9 = section[offset + 9].toInt() and 0xFF
            val titleLength = b9

            val title = if (titleLength > 0 && offset + 10 + titleLength <= section.size) {
                decodeMultipleStringStructure(section, offset + 10, titleLength)
            } else ""

            val descOffset = offset + 10 + titleLength
            val descriptorsLength = if (descOffset + 2 <= section.size) {
                readUInt16(section, descOffset) and 0x03FF
            } else 0

            // TODO: Parse content_advisory_descriptor for rating (table_id 0x87)

            offset += 10 + titleLength + 2 + descriptorsLength

            events += EitEvent(
                eventId = eventId,
                startTimeGps = gpsTime,
                startTimeMs = unixMs,
                durationSeconds = durationSec,
                title = title,
                rating = null,
            )
        }

        return ParsedEit(sourceId = sourceId, events = events)
    }

    private fun gpsToUnixMs(gpsSec: Long): Long {
        val unixSec = GPS_EPOCH_UNIX_SEC + gpsSec - GPS_LEAP_SECONDS
        return unixSec * 1000L
    }

    /**
     * Decode ATSC A/65 Multiple String Structure.
     * This is a simplified decoder handling only English (ISO-Latin-1 / UTF-8).
     * A full implementation handles all ATSC compression and encoding modes.
     * TODO: Implement full A/65 Annex B decoder (Huffman tables, UTF-16, etc.)
     */
    fun decodeMultipleStringStructure(data: ByteArray, offset: Int, length: Int): String {
        if (offset + length > data.size || length < 4) return ""
        val numStrings = data[offset].toInt() and 0xFF
        if (numStrings == 0) return ""

        var off = offset + 1
        val sb = StringBuilder()

        for (s in 0 until numStrings) {
            if (off + 4 > offset + length) break
            // lang (3 bytes) + num_segments (1 byte)
            off += 3
            val numSegments = data[off++].toInt() and 0xFF
            for (seg in 0 until numSegments) {
                if (off + 3 > offset + length) break
                val compType = data[off++].toInt() and 0xFF
                val mode = data[off++].toInt() and 0xFF
                val numBytes = data[off++].toInt() and 0xFF
                val segEnd = off + numBytes
                if (segEnd > offset + length) break

                when {
                    compType == 0x00 && mode == 0x3F -> {
                        // No compression, ISO-Latin-1 (0x3F)
                        sb.append(String(data, off, numBytes, Charsets.ISO_8859_1))
                    }
                    compType == 0x00 && (mode == 0x00 || mode == 0x01) -> {
                        // No compression, Unicode (UTF-16)
                        sb.append(String(data, off, numBytes, Charsets.UTF_16BE))
                    }
                    compType == 0x00 -> {
                        // Best-effort: treat as ASCII
                        sb.append(String(data, off, numBytes, Charsets.US_ASCII))
                    }
                    else -> {
                        // Huffman/other compression — TODO: implement A/65 Annex B tables
                        sb.append("[encoded]")
                    }
                }
                off = segEnd
            }
        }
        return sb.toString().trim()
    }

    private fun readUInt16(buf: ByteArray, off: Int): Int =
        ((buf[off].toInt() and 0xFF) shl 8) or (buf[off + 1].toInt() and 0xFF)

    private fun readUInt24(buf: ByteArray, off: Int): Int =
        ((buf[off].toInt() and 0xFF) shl 16) or
        ((buf[off + 1].toInt() and 0xFF) shl 8) or
        (buf[off + 2].toInt() and 0xFF)

    private fun readUInt32(buf: ByteArray, off: Int): Long =
        ((buf[off].toLong() and 0xFF) shl 24) or
        ((buf[off + 1].toLong() and 0xFF) shl 16) or
        ((buf[off + 2].toLong() and 0xFF) shl 8) or
        (buf[off + 3].toLong() and 0xFF)
}
