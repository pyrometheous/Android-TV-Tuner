package dev.tvtuner.parser.atsc

/**
 * Constants for MPEG-2 Transport Stream (MPEG-2 TS) structure.
 * Reference: ISO/IEC 13818-1
 */
object TsConstants {
    const val PACKET_SIZE = 188
    const val SYNC_BYTE = 0x47.toByte()

    // Well-known PIDs
    const val PID_PAT = 0x0000
    const val PID_CAT = 0x0001
    const val PID_NIT = 0x0010
    const val PID_ATSC_BASE = 0x1FFB  // PSIP base table PID for ATSC

    // ATSC Table IDs (PSIP A/65)
    const val TABLE_ID_PAT = 0x00
    const val TABLE_ID_PMT = 0x02
    const val TABLE_ID_MGT = 0xC7
    const val TABLE_ID_TVCT = 0xC8  // Terrestrial VCT
    const val TABLE_ID_CVCT = 0xC9  // Cable VCT
    const val TABLE_ID_RRT = 0xCA
    const val TABLE_ID_EIT_0 = 0xCB  // EIT for current/next (sub-table 0)
    const val TABLE_ID_EIT_1 = 0xCC
    const val TABLE_ID_EIT_2 = 0xCD
    const val TABLE_ID_EIT_3 = 0xCE
    const val TABLE_ID_ETT = 0xCC  // Extended Text Table (same range as EIT-1..3, distinguished by table_id_ext)
    const val TABLE_ID_STT = 0xCD  // System Time Table
}
