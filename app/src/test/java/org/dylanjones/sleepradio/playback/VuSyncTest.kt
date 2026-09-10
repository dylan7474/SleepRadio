package org.dylanjones.sleepradio.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/** Phase 16A — which VU-meter delay applies for the current route / mode. */
class VuSyncTest {

    @Test
    fun `auto uses the phone value on the speaker route`() {
        assertEquals(120, resolveVuDelayMs(auto = true, isBluetooth = false, 120, 260, 150))
    }

    @Test
    fun `auto uses the bluetooth value on a bluetooth route`() {
        assertEquals(260, resolveVuDelayMs(auto = true, isBluetooth = true, 120, 260, 150))
    }

    @Test
    fun `manual ignores the route and uses the custom value`() {
        assertEquals(150, resolveVuDelayMs(auto = false, isBluetooth = false, 120, 260, 150))
        assertEquals(150, resolveVuDelayMs(auto = false, isBluetooth = true, 120, 260, 150))
    }
}
