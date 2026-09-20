package com.erinlkolp.equalizer

import kotlin.math.roundToInt

object EqHelper {
    // Maps 0-240 slider progress to -12.0 to 12.0 dB
    fun progressToGainDb(progress: Int): Float {
        return (progress - 120) / 10.0f
    }

    // Maps -12.0 to 12.0 dB back to 0-240 slider progress
    fun gainDbToProgress(gainDb: Float): Int {
        return (gainDb * 10.0f).roundToInt() + 120
    }

    // Formats frequency for UI (e.g., 1000 -> "1k")
    fun formatFrequency(freq: Int): String {
        return if (freq >= 1000) "${freq / 1000}k" else "$freq"
    }

    /**
     * Calculates the negative preamp headroom required to prevent digital clipping
     * based on the highest boosted band in [gains].
     * If no bands are boosted (> 120, i.e., > 0 dB), returns 120 (0.0 dB).
     * If highest boost is +6.0 dB (progress 180), returns 60 (-6.0 dB).
     */
    fun calculateAutoHeadroomProgress(gains: IntArray): Int {
        if (gains.isEmpty()) return 120
        val maxProgress = gains.maxOrNull() ?: 120
        return if (maxProgress > 120) {
            (240 - maxProgress).coerceIn(0, 240)
        } else {
            120
        }
    }

    /**
     * Smooths an array of 31 band gains using a 3-point weighted moving average [0.25, 0.5, 0.25].
     * Preserves edge boundaries and clamps outputs within 0..240.
     */
    fun smoothGains(gains: IntArray): IntArray {
        if (gains.size <= 2) return gains.clone()
        val result = IntArray(gains.size)
        result[0] = ((2 * gains[0] + gains[1]) / 3.0).roundToInt().coerceIn(0, 240)
        for (i in 1 until gains.size - 1) {
            result[i] = ((gains[i - 1] + 2 * gains[i] + gains[i + 1]) / 4.0).roundToInt().coerceIn(0, 240)
        }
        result[gains.size - 1] = ((gains[gains.size - 2] + 2 * gains[gains.size - 1]) / 3.0).roundToInt().coerceIn(0, 240)
        return result
    }
}
