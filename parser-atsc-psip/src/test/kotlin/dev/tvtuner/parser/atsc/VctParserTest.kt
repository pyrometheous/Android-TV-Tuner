package dev.tvtuner.parser.atsc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VctParserTest {

    @Test
    fun `parse returns null for wrong table id`() {
        val section = ByteArray(32) { 0x00.toByte() }
        section[0] = 0x00 // PAT table id, not VCT
        assertNull(VctParser.parse(section))
    }

    @Test
    fun `parse returns null for too-short section`() {
        assertNull(VctParser.parse(ByteArray(5)))
    }

    @Test
    fun `parse returns empty vct for zero channels`() {
        val section = buildVctSection(emptyList())
        val result = VctParser.parse(section)
        assertNotNull(result)
        assertEquals(0, result!!.channels.size)
    }

    /**
     * Builds a minimal well-formed TVCT section with zero channels for testing.
     */
    private fun buildVctSection(channels: List<VctChannel>): ByteArray {
        // Minimal 10-byte TVCT header, 0 channel records, 2-byte table_length_in_section=0
        return byteArrayOf(
            TsConstants.TABLE_ID_TVCT.toByte(), // table_id
            0xF0.toByte(), 0x09.toByte(),       // section_syntax=1, private=0, reserved=3, section_length=9
            0x00.toByte(), 0x01.toByte(),       // transport_stream_id
            0xC1.toByte(),                       // reserved, version, current_next
            0x00.toByte(),                       // section_number
            0x00.toByte(),                       // last_section_number
            0x00.toByte(),                       // protocol_version
            0x00.toByte(),                       // num_channels_in_section = 0
            // additional_descriptors_length (2 bytes):
            0x00.toByte(), 0x00.toByte(),
            // CRC32 placeholder (4 bytes):
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        )
    }
}
