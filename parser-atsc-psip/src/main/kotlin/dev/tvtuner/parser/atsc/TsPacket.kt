package dev.tvtuner.parser.atsc

/**
 * Parses raw MPEG-2 TS 188-byte packets into their header fields.
 */
data class TsPacket(
    val syncByte: Byte,
    val transportErrorIndicator: Boolean,
    val payloadUnitStartIndicator: Boolean,
    val transportPriority: Boolean,
    val pid: Int,
    val scramblingControl: Int,
    val hasAdaptationField: Boolean,
    val hasPayload: Boolean,
    val continuityCounter: Int,
    // Raw payload bytes (may be empty)
    val payload: ByteArray,
) {
    val isValid: Boolean get() = syncByte == TsConstants.SYNC_BYTE && !transportErrorIndicator

    companion object {
        fun parse(data: ByteArray, offset: Int = 0): TsPacket? {
            if (data.size - offset < TsConstants.PACKET_SIZE) return null
            val sync = data[offset]
            if (sync != TsConstants.SYNC_BYTE) return null

            val b1 = data[offset + 1].toInt() and 0xFF
            val b2 = data[offset + 2].toInt() and 0xFF
            val b3 = data[offset + 3].toInt() and 0xFF

            val tei = (b1 and 0x80) != 0
            val pusi = (b1 and 0x40) != 0
            val priority = (b1 and 0x20) != 0
            val pid = ((b1 and 0x1F) shl 8) or b2
            val scrambling = (b3 shr 6) and 0x03
            val hasAF = (b3 and 0x20) != 0
            val hasPL = (b3 and 0x10) != 0
            val cc = b3 and 0x0F

            var payloadStart = 4
            if (hasAF) {
                val afLen = data[offset + 4].toInt() and 0xFF
                payloadStart = 5 + afLen
            }
            val payloadEnd = offset + TsConstants.PACKET_SIZE
            val payload = if (hasPL && payloadStart < payloadEnd) {
                data.copyOfRange(offset + payloadStart, payloadEnd)
            } else ByteArray(0)

            return TsPacket(sync, tei, pusi, priority, pid, scrambling, hasAF, hasPL, cc, payload)
        }
    }
}
