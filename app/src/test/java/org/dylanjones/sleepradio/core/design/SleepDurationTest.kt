package org.dylanjones.sleepradio.core.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepDurationTest {

    @Test
    fun `fraction 0 maps to 15 minutes`() {
        assertEquals(15, sleepMinutesFor(0f))
    }

    @Test
    fun `fraction 1 maps to 60 minutes`() {
        assertEquals(60, sleepMinutesFor(1f))
    }

    @Test
    fun `one third maps to 30 minutes`() {
        assertEquals(30, sleepMinutesFor(1f / 3f))
    }

    @Test
    fun `out-of-range fractions are clamped`() {
        assertEquals(15, sleepMinutesFor(-2f))
        assertEquals(60, sleepMinutesFor(5f))
    }

    @Test
    fun `result is always a multiple of 5 within 15_60`() {
        for (i in 0..100) {
            val m = sleepMinutesFor(i / 100f)
            assertEquals(0, m % 5)
            assertTrue(m in 15..60)
        }
    }
}
