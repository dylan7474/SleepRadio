package org.dylanjones.sleepradio.core.design

import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the hand-copied artwork geometry (from tools/keyart/regions.json) against a bad edit. */
class ChannelKeyArtTest {
    private val canvas = ArtRect(0f, 0f, ChannelKeyArt.WIDTH, ChannelKeyArt.HEIGHT)

    private fun inside(r: ArtRect) = r.x >= canvas.x && r.y >= canvas.y && r.right <= canvas.right && r.bottom <= canvas.bottom

    @Test fun `every window sits inside the sprite, including the latch travel`() {
        for (r in listOf(ChannelKeyArt.NUMBER, ChannelKeyArt.LABEL, ChannelKeyArt.LAMP)) {
            assertTrue("$r", inside(r))
            // a latched key is drawn TRAVEL lower, so its windows must still fit
            assertTrue("$r does not fit when latched", r.bottom + ChannelKeyArt.TRAVEL + ChannelKeyArt.PRESS_EXTRA <= canvas.bottom)
        }
    }

    @Test fun `number window, label window and lamp run left to right without overlapping`() {
        assertTrue(ChannelKeyArt.NUMBER.right < ChannelKeyArt.LABEL.x)
        assertTrue(ChannelKeyArt.LABEL.right < ChannelKeyArt.LAMP.x)
    }
}
