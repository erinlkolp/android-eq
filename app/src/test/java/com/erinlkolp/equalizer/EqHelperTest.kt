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
}
