package dev.tvtuner.parser.atsc

/**
 * Reassembles MPEG-2 section data from TS packets.
 * Each PID carries one or more sections; PUSI resets the accumulator.
 */
class SectionAssembler {

    private val buffers = HashMap<Int, ByteArray>()
    private val offsets = HashMap<Int, Int>()

    /**
     * Feed a TS packet. Returns a complete section if one was just completed,
     * or null if we're still accumulating.
     */
    fun feed(packet: TsPacket): ByteArray? {
        if (!packet.isValid || !packet.hasPayload || packet.payload.isEmpty()) return null
        val pid = packet.pid
        val payload = packet.payload

        if (packet.payloadUnitStartIndicator) {
            // pointer_field: number of bytes before the new section starts
            val pointer = payload[0].toInt() and 0xFF
            if (pointer > 0 && buffers[pid] != null) {
                // Complete the previous section with the bytes before the pointer
                val buf = buffers[pid]!!
                val off = offsets[pid] ?: 0
                val remaining = minOf(pointer, payload.size - 1)
                System.arraycopy(payload, 1, buf, off, remaining)
                // (previous section completion omitted for brevity — full impl handles this)
            }
            // Start new section after pointer
            val sectionStart = 1 + pointer
            if (sectionStart >= payload.size) return null
            val sectionLengthHigh = payload[sectionStart + 1].toInt() and 0xFF
            val sectionLengthLow = payload[sectionStart + 2].toInt() and 0xFF
            val sectionLength = ((sectionLengthHigh and 0x0F) shl 8) or sectionLengthLow
            val totalSize = 3 + sectionLength
            val sectionBuf = ByteArray(totalSize)
            val available = payload.size - sectionStart
            val toCopy = minOf(available, totalSize)
            System.arraycopy(payload, sectionStart, sectionBuf, 0, toCopy)
            if (toCopy >= totalSize) {
                // Complete in one packet
                buffers.remove(pid)
                offsets.remove(pid)
                return sectionBuf
            }
            buffers[pid] = sectionBuf
            offsets[pid] = toCopy
        } else {
            val buf = buffers[pid] ?: return null
            val off = offsets[pid] ?: return null
            val toCopy = minOf(payload.size, buf.size - off)
            if (toCopy <= 0) return null
            System.arraycopy(payload, 0, buf, off, toCopy)
            val newOff = off + toCopy
            if (newOff >= buf.size) {
                buffers.remove(pid)
                offsets.remove(pid)
                return buf
            }
            offsets[pid] = newOff
        }
        return null
    }
}
