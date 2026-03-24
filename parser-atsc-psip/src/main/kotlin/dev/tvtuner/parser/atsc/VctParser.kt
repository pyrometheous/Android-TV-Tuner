package dev.tvtuner.parser.atsc

/**
 * Parsed result of a PSIP Virtual Channel Table (VCT) section.
 * Covers both TVCT (0xC8) and CVCT (0xC9).
 *
 * Reference: ATSC A/65:2013 §6.4
 */
data class ParsedVct(
    val channels: List<VctChannel>,
)

data class VctChannel(
    val shortName: String,         // Up to 7 UTF-16 characters
    val majorChannelNumber: Int,   // 10-bit
    val minorChannelNumber: Int,   // 10-bit
    val modulationMode: Int,
    val carrierFrequency: Long,    // Hz
    val channelTsid: Int,
    val programNumber: Int,
    val etmLocation: Int,
    val accessControlled: Boolean,
    val hidden: Boolean,
    val serviceType: Int,
    val sourceId: Int,
)

object VctParser {

    /**
     * Parse a TVCT or CVCT section.
     * @param section Raw section bytes starting from table_id byte.
     */
    fun parse(section: ByteArray): ParsedVct? {
        if (section.size < 10) return null
        val tableId = section[0].toInt() and 0xFF
        if (tableId != TsConstants.TABLE_ID_TVCT && tableId != TsConstants.TABLE_ID_CVCT) return null

        val numChannelsDefinitions = section[9].toInt() and 0xFF
        val channels = mutableListOf<VctChannel>()
        var offset = 10

        repeat(numChannelsDefinitions) {
            if (offset + 32 > section.size) return@repeat
            // Short_name: 14 bytes, UTF-16
            val nameBytes = section.copyOfRange(offset, offset + 14)
            val shortName = buildString {
                for (i in 0 until 7) {
                    val hi = nameBytes[i * 2].toInt() and 0xFF
                    val lo = nameBytes[i * 2 + 1].toInt() and 0xFF
                    val ch = (hi shl 8) or lo
                    if (ch == 0) return@buildString
                    append(ch.toChar())
                }
            }.trim()

            val b14 = section[offset + 14].toInt() and 0xFF
            val b15 = section[offset + 15].toInt() and 0xFF
            val b16 = section[offset + 16].toInt() and 0xFF
            val majorChannel = ((b14 and 0x0F) shl 6) or (b15 shr 2)
            val minorChannel = ((b15 and 0x03) shl 8) or b16

            val modulationMode = section[offset + 17].toInt() and 0xFF

            val freq = readUInt32(section, offset + 18)

            val tsid = readUInt16(section, offset + 22)
            val programNumber = readUInt16(section, offset + 24)

            val etmLoc = (section[offset + 26].toInt() and 0xFF) shr 6
            val accessControlled = (section[offset + 26].toInt() and 0x20) != 0
            val hidden = (section[offset + 26].toInt() and 0x10) != 0
            val serviceType = section[offset + 27].toInt() and 0x3F
            val sourceId = readUInt16(section, offset + 28)

            val descriptorsLength = readUInt16(section, offset + 30) and 0x03FF
            offset += 32 + descriptorsLength

            channels += VctChannel(
                shortName = shortName,
                majorChannelNumber = majorChannel,
                minorChannelNumber = minorChannel,
                modulationMode = modulationMode,
                carrierFrequency = freq,
                channelTsid = tsid,
                programNumber = programNumber,
                etmLocation = etmLoc,
                accessControlled = accessControlled,
                hidden = hidden,
                serviceType = serviceType,
                sourceId = sourceId,
            )
        }

        return ParsedVct(channels)
    }

    private fun readUInt16(buf: ByteArray, off: Int): Int =
        ((buf[off].toInt() and 0xFF) shl 8) or (buf[off + 1].toInt() and 0xFF)

    private fun readUInt32(buf: ByteArray, off: Int): Long =
        ((buf[off].toLong() and 0xFF) shl 24) or
        ((buf[off + 1].toLong() and 0xFF) shl 16) or
        ((buf[off + 2].toLong() and 0xFF) shl 8) or
        (buf[off + 3].toLong() and 0xFF)
}
