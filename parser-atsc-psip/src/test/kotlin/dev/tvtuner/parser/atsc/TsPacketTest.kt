package dev.tvtuner.parser.atsc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TsPacketTest {

    @Test
    fun `parse returns null for too-short data`() {
        assert(TsPacket.parse(ByteArray(100)) == null)
    }

    @Test
    fun `parse returns null for wrong sync byte`() {
        val data = ByteArray(TsConstants.PACKET_SIZE) { 0x00.toByte() }
        assert(TsPacket.parse(data) == null)
    }

    @Test
    fun `parse extracts PID correctly`() {
        val data = ByteArray(TsConstants.PACKET_SIZE) { 0x00.toByte() }
        data[0] = TsConstants.SYNC_BYTE
        // PID = 0x1FFB (ATSC base)
        data[1] = 0x1F.toByte()
        data[2] = 0xFB.toByte()
        data[3] = 0x10.toByte() // hasPayload = true
        val packet = TsPacket.parse(data)
        assertNotNull(packet)
        assertEquals(0x1FFB, packet!!.pid)
    }
}
