package org.dylanjones.sleepradio.core.design

import java.io.File
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

    // ---- the tall key (full-screen channel button, portrait) ----

    private val tall = ChannelKeyArt.TALL

    @Test fun `tall key windows sit inside the sprite, latched too, and stack top to bottom`() {
        for (r in listOf(tall.number, tall.label, tall.lamp)) {
            assertTrue("$r", r.x >= 0f && r.y >= 0f && r.right <= tall.width && r.bottom <= tall.height)
            assertTrue("$r latched", r.bottom + ChannelKeyArt.TRAVEL + ChannelKeyArt.PRESS_EXTRA <= tall.height)
        }
        assertTrue(tall.number.bottom < tall.label.y)
        assertTrue(tall.label.bottom < tall.lamp.y)
        assertTrue("the name window is most of the key", tall.label.h > tall.height * 0.4f)
    }

    // ---- the broad key (full-screen channel button, landscape) ----

    @Test fun `broad key windows sit inside the sprite, latched too, left to right, with a bigger name window`() {
        val broad = ChannelKeyArt.BROAD
        for (r in listOf(broad.number, broad.label, broad.lamp)) {
            assertTrue("$r", r.x >= 0f && r.y >= 0f && r.right <= broad.width && r.bottom <= broad.height)
            assertTrue("$r latched", r.bottom + ChannelKeyArt.TRAVEL + ChannelKeyArt.PRESS_EXTRA <= broad.height)
        }
        assertTrue(broad.number.right < broad.label.x)
        assertTrue(broad.label.right < broad.lamp.x)
        // Same height as the wide key, so at the same size the name window is simply bigger.
        assertEquals(ChannelKeyArt.HEIGHT, broad.height, 0f)
        assertTrue(broad.label.w > ChannelKeyArt.LABEL.w * 1.4f && broad.label.h > ChannelKeyArt.LABEL.h * 1.2f)
    }

    @Test fun `the hand-copied geometry matches what make_keys wrote`() {
        fun regions(name: String): String = File("../tools/keyart/$name").readText().replace(Regex("\\s"), "")
        fun num(json: String, block: String, key: String): Float =
            Regex("\"$block\":\\{[^}]*\"$key\":(-?[0-9.]+)").find(json)!!.groupValues[1].toFloat()
        for ((json, art) in listOf(regions("regions.json") to ChannelKeyArt.WIDE, regions("tall_regions.json") to tall, regions("broad_regions.json") to ChannelKeyArt.BROAD)) {
            val canvas = Regex("\"canvas\":\\[([0-9.]+),([0-9.]+)]").find(json)!!.groupValues
            assertEquals(canvas[1].toFloat(), art.width, 0f)
            assertEquals(canvas[2].toFloat(), art.height, 0f)
            assertEquals(ArtRect(num(json, "plate", "x"), num(json, "plate", "y"), num(json, "plate", "w"), num(json, "plate", "h")), art.number)
            assertEquals(ArtRect(num(json, "window", "x"), num(json, "window", "y"), num(json, "window", "w"), num(json, "window", "h")), art.label)
            val d = num(json, "lamp", "d")
            assertEquals(ArtRect(num(json, "lamp", "cx") - d / 2, num(json, "lamp", "cy") - d / 2, d, d), art.lamp)
        }
    }

    private fun fits(label: String, font: Float, w: Float, h: Float, maxLines: Int = 3): Boolean {
        val maxChars = (w / (font * ChannelKeyArt.CONDENSED_EM_PER_CHAR)).toInt()
        val words = label.split(" ")
        if (words.any { it.length > maxChars }) return false
        var lines = 1; var used = 0
        for (word in words) {
            used = if (used == 0) word.length else if (used + 1 + word.length <= maxChars) used + 1 + word.length else { lines++; word.length }
        }
        return lines <= maxLines && lines * font * ChannelKeyArt.TALL_LINE_HEIGHT <= h
    }

    @Test fun `the tall name fits its window, wrapping at spaces, never splitting a word`() {
        val w = 282f; val h = 276f                                 // the name window on a ~412 dp wide phone
        for (name in listOf("RADIO CAROLINE", "BBC RADIO 4 EXTRA", "BROADCAST", "SLEEP MUSIC FOR A RAINY NIGHT", "FM 94.7", "X")) {
            val font = ChannelKeyArt.fitLabelFontDp(name, w, h)
            assertTrue("$name at $font dp", fits(name, font, w, h))
            assertTrue("$name at $font dp: a bit bigger would still fit, so it isn't as big as it could be",
                !fits(name, font / 0.96f, w, h) || font >= h / 3f - 0.01f)
        }
    }

    @Test fun `the tall name is far bigger than the big-button name`() {
        val tallFont = ChannelKeyArt.fitLabelFontDp("RADIO CAROLINE", 282f, 276f)
        val bigFont = ChannelKeyArt.bigLabelFontDp(ChannelKeyArt.LABEL.h * 0.64f)      // BIG mode, full phone width
        assertTrue("tall $tallFont dp vs big $bigFont dp", tallFont > 1.5f * bigFont)
        assertTrue(ChannelKeyArt.fitLabelFontDp("", 282f, 276f) > 0f)
    }

    @Test fun `in the wide key the full-screen name takes at most two lines`() {
        val w = 284f; val h = 135f                                 // the wide key's name window, full screen in landscape
        for (name in listOf("SLEEPRADIO BROADCAST", "RADIO CAROLINE", "BBC")) {
            val font = ChannelKeyArt.fitLabelFontDp(name, w, h, maxLines = 2)
            assertTrue("$name at $font dp", fits(name, font, w, h, maxLines = 2))
            assertTrue(font <= h / 2f)
        }
    }
}
