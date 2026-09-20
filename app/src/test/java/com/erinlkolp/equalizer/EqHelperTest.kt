package com.erinlkolp.equalizer

import org.junit.Assert.assertEquals
import org.junit.Test

class EqHelperTest {

    @Test
    fun testProgressToGainDb_center() {
        val gain = EqHelper.progressToGainDb(120)
        assertEquals(0.0f, gain, 0.001f)
    }

    @Test
    fun testProgressToGainDb_min() {
        val gain = EqHelper.progressToGainDb(0)
        assertEquals(-12.0f, gain, 0.001f)
    }

    @Test
    fun testProgressToGainDb_max() {
        val gain = EqHelper.progressToGainDb(240)
        assertEquals(12.0f, gain, 0.001f)
    }

    @Test
    fun testFormatFrequency_Hz() {
        assertEquals("500", EqHelper.formatFrequency(500))
        assertEquals("63", EqHelper.formatFrequency(63))
    }

    @Test
    fun testFormatFrequency_kHz() {
        assertEquals("1k", EqHelper.formatFrequency(1000))
        assertEquals("16k", EqHelper.formatFrequency(16000))
    }

    @Test
    fun testGainDbToProgress() {
        assertEquals(120, EqHelper.gainDbToProgress(0.0f))
        assertEquals(0, EqHelper.gainDbToProgress(-12.0f))
        assertEquals(240, EqHelper.gainDbToProgress(12.0f))
        assertEquals(180, EqHelper.gainDbToProgress(6.0f))
    }

    @Test
    fun testCalculateAutoHeadroomProgress_flat() {
        val flatGains = IntArray(31) { 120 }
        assertEquals(120, EqHelper.calculateAutoHeadroomProgress(flatGains))
    }

    @Test
    fun testCalculateAutoHeadroomProgress_cutsOnly() {
        val cuts = IntArray(31) { 100 }
        assertEquals(120, EqHelper.calculateAutoHeadroomProgress(cuts))
    }

    @Test
    fun testCalculateAutoHeadroomProgress_boosted() {
        val boosted = IntArray(31) { 120 }.apply { this[5] = 180 } // +6 dB boost
        assertEquals(60, EqHelper.calculateAutoHeadroomProgress(boosted)) // -6 dB headroom

        val maxBoost = IntArray(31) { 120 }.apply { this[10] = 240 } // +12 dB boost
        assertEquals(0, EqHelper.calculateAutoHeadroomProgress(maxBoost)) // -12 dB headroom
    }

    @Test
    fun testSmoothGains() {
        val impulse = IntArray(5) { 120 }.apply { this[2] = 200 }
        val smoothed = EqHelper.smoothGains(impulse)
        assertEquals(5, smoothed.size)
        // Center should be lowered, neighbors raised
        assertEquals(120, smoothed[0])
        assertEquals(140, smoothed[1]) // (120 + 2*120 + 200)/4 = 560/4 = 140
        assertEquals(160, smoothed[2]) // (120 + 2*200 + 120)/4 = 640/4 = 160
        assertEquals(140, smoothed[3])
        assertEquals(120, smoothed[4])
    }
}
