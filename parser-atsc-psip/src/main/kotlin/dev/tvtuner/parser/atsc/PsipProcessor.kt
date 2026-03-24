package dev.tvtuner.parser.atsc

/**
 * High-level PSIP stream processor.
 *
 * Reads raw TS packets, reassembles sections by PID, and emits parsed PSIPEvents.
 * Intended to run as a coroutine collecting from [TunerBackend.readTransportStream].
 */
class PsipProcessor {

    private val patAssembler = SectionAssembler()
    private val psipAssembler = SectionAssembler()

    /**
     * Process a 188-byte TS packet.
     * Returns zero or more [PsipEvent]s parsed from the packet.
     */
    fun process(rawPacket: ByteArray): List<PsipEvent> {
        val packet = TsPacket.parse(rawPacket) ?: return emptyList()
        if (!packet.isValid) return emptyList()

        val events = mutableListOf<PsipEvent>()

        when (packet.pid) {
            TsConstants.PID_PAT -> {
                val section = patAssembler.feed(packet)
                if (section != null) {
                    // TODO: parse PAT to discover PMT PIDs for each program
                }
            }
            TsConstants.PID_ATSC_BASE -> {
                val section = psipAssembler.feed(packet) ?: return emptyList()
                val tableId = section[0].toInt() and 0xFF
                when (tableId) {
                    TsConstants.TABLE_ID_TVCT,
                    TsConstants.TABLE_ID_CVCT -> {
                        VctParser.parse(section)?.let { events += PsipEvent.VctParsed(it) }
                    }
                    TsConstants.TABLE_ID_EIT_0,
                    TsConstants.TABLE_ID_EIT_1,
                    TsConstants.TABLE_ID_EIT_2,
                    TsConstants.TABLE_ID_EIT_3 -> {
                        EitParser.parse(section)?.let { events += PsipEvent.EitParsed(it) }
                    }
                }
            }
        }
        return events
    }
}

sealed class PsipEvent {
    data class VctParsed(val vct: ParsedVct) : PsipEvent()
    data class EitParsed(val eit: ParsedEit) : PsipEvent()
}
