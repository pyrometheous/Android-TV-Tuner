package dev.tvtuner.tuner.usb

/**
 * ATSC North America RF channel frequency plan.
 * Center frequencies in kHz.
 * Reference: FCC §73.603 / ATSC A/65:2013
 */
object AtscFrequencyPlan {

    data class RfChannel(val rfChannel: Int, val centerFreqKhz: Int) {
        /** Center frequency in Hz (for demodulator register writes). */
        val centerFreqHz: Long get() = centerFreqKhz.toLong() * 1_000L
    }

    /** Full US ATSC frequency plan — channels 2–51 (post-repack). */
    val ALL: List<RfChannel> = buildList {
        // VHF Low band (channels 2–6)
        add(RfChannel(2,  57_000))
        add(RfChannel(3,  63_000))
        add(RfChannel(4,  69_000))
        add(RfChannel(5,  79_000))
        add(RfChannel(6,  85_000))
        // VHF High band (channels 7–13)
        add(RfChannel(7,  177_000))
        add(RfChannel(8,  183_000))
        add(RfChannel(9,  189_000))
        add(RfChannel(10, 195_000))
        add(RfChannel(11, 201_000))
        add(RfChannel(12, 207_000))
        add(RfChannel(13, 213_000))
        // UHF band (channels 14–51; channels 52+ returned to FCC after repack)
        for (ch in 14..51) {
            add(RfChannel(ch, 470_000 + (ch - 14) * 6_000))
        }
    }

    /**
     * Quick scan: UHF channels 14–51 only.
     * Since the 2020 FCC repack all US digital broadcast is in UHF.
     */
    val QUICK: List<RfChannel> = ALL.filter { it.rfChannel >= 14 }

    /** Convert an RF channel number to its center frequency in kHz. */
    fun channelToFreqKhz(rfChannel: Int): Int? =
        ALL.firstOrNull { it.rfChannel == rfChannel }?.centerFreqKhz
}
