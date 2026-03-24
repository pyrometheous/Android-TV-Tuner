package dev.tvtuner.parser.atsc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class EitParserTest {

    @Test
    fun `decodeMultipleStringStructure handles latin-1 segment`() {
        // Minimal MSS: 1 string, lang="eng", 1 segment, compType=0, mode=0x3F, bytes="Hello"
        val text = "Hello"
        val textBytes = text.toByteArray(Charsets.ISO_8859_1)
        val mss = buildMss("eng", 0x00, 0x3F, textBytes)
        val result = EitParser.decodeMultipleStringStructure(mss, 0, mss.size)
        assertEquals("Hello", result)
    }

    @Test
    fun `parse returns null for non-EIT table id`() {
        val section = ByteArray(16) { 0x00.toByte() }
        section[0] = TsConstants.TABLE_ID_TVCT.toByte()
        val result = EitParser.parse(section)
        assert(result == null) { "Expected null for non-EIT table" }
    }

    private fun buildMss(lang: String, compType: Int, mode: Int, data: ByteArray): ByteArray {
        val langBytes = lang.toByteArray(Charsets.US_ASCII).copyOf(3)
        return byteArrayOf(
            0x01.toByte(),              // num_strings = 1
            langBytes[0], langBytes[1], langBytes[2],
            0x01.toByte(),              // num_segments = 1
            compType.toByte(),
            mode.toByte(),
            data.size.toByte(),
        ) + data
    }
}
