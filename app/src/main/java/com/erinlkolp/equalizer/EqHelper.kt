package com.erinlkolp.equalizer

object EqHelper {
    // Maps 0-240 slider progress to -12.0 to 12.0 dB
    fun progressToGainDb(progress: Int): Float {
        return (progress - 120) / 10.0f
    }

    // Formats frequency for UI (e.g., 1000 -> "1k")
    fun formatFrequency(freq: Int): String {
        return if (freq >= 1000) "${freq / 1000}k" else "$freq"
    }
}
