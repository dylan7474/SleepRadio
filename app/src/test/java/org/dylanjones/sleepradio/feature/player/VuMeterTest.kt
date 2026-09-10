package org.dylanjones.sleepradio.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** Phase 15 — the analogue VU needle mapping (−30..0 dBFS across the sweep). */
class VuMeterTest {

    @Test
    fun `silence rests the needle`() {
        assertEquals(0f, vuNeedleFraction(0f), 0f)
    }

    @Test
    fun `anything below the floor reads zero`() {
        assertEquals(0f, vuNeedleFraction(0.001f), 0f) // ≈ −60 dBFS
        assertEquals(0f, vuNeedleFraction(10.0.pow(-30.0 / 20.0).toFloat()), 0f) // exactly −30 dBFS
    }

    @Test
    fun `full scale pegs the needle`() {
        assertEquals(1f, vuNeedleFraction(1f), 1e-4f)
    }

    @Test
    fun `above full scale is clamped`() {
        assertEquals(1f, vuNeedleFraction(4f), 0f)
    }

    @Test
    fun `mid scale sits mid sweep`() {
        // −15 dBFS is the midpoint of a −30..0 range.
        val peak = 10.0.pow(-15.0 / 20.0).toFloat()
        assertEquals(0.5f, vuNeedleFraction(peak), 0.02f)
    }

    @Test
    fun `mapping is monotonic`() {
        assertTrue(vuNeedleFraction(0.1f) < vuNeedleFraction(0.4f))
        assertTrue(vuNeedleFraction(0.4f) < vuNeedleFraction(0.9f))
    }
}
