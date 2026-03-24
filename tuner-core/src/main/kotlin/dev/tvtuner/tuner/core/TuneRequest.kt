package dev.tvtuner.tuner.core

/**
 * Represents a physical broadcast channel that a tuner can be directed to.
 * Distinct from the logical/virtual channel the user sees.
 */
data class TuneRequest(
    /** RF channel frequency in kHz */
    val rfChannelKhz: Int,
    /** MPEG-2 program number to demux; 0 = let backend choose first valid */
    val programNumber: Int = 0,
)

data class SignalMetrics(
    /** Signal-to-noise ratio in dB; null if not available */
    val snrDb: Float?,
    /** Signal quality as 0–100 percent; null if not available */
    val qualityPercent: Int?,
    /** Signal strength in dBm; null if not available */
    val strengthDbm: Float?,
    /** Whether the tuner is locked to the signal */
    val isLocked: Boolean,
)
