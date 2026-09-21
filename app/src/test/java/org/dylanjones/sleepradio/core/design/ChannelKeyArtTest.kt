package org.dylanjones.sleepradio.core.design

import org.junit.Assert.assertEquals
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

    @Test fun `big label fills the window height for short values and is limited by width for long ones`() {
        val w = 88f; val h = 39f
        assertTrue(ChannelKeyArt.fillFontSizeDp(2, w, h) == h * 1.15f)                       // "30": height-limited
        assertTrue(ChannelKeyArt.fillFontSizeDp(3, w, h) <= h * 1.15f)                       // "142": still fits
        assertTrue(3 * 0.55f * ChannelKeyArt.fillFontSizeDp(3, w, h) <= w + 0.01f)           // ...within the width
        assertTrue(5 * 0.55f * ChannelKeyArt.fillFontSizeDp(5, w, h) <= w + 0.01f)           // even a silly 5 digits
        assertTrue(ChannelKeyArt.fillFontSizeDp(0, w, h) > 0f)                               // empty text can't divide by zero
    }

    @Test fun `the big-key name is about half the window height, well above the normal label size`() {
        val scale = 0.53f                                        // dp per art pixel at a typical phone size
        val windowH = ChannelKeyArt.LABEL.h * scale
        val font = ChannelKeyArt.bigLabelFontDp(windowH)
        assertEquals(windowH * 0.5f, font, 1e-4f)
        assertTrue("$font dp", font > 1.5f * (0.060f * ChannelKeyArt.WIDTH * scale))   // more than 1.5x the normal 17-18 dp
    }

    @Test fun `the big-key name fits the window height on one line`() {
        val windowH = ChannelKeyArt.LABEL.h * 0.53f
        assertTrue(ChannelKeyArt.bigLabelFontDp(windowH) * 1.1f < windowH)                 // one line, with its line height
    }
}
