package org.dylanjones.sleepradio.core.design

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SteamMathTest {
    @Test fun `progress is position over duration, clamped, and zero when there is no duration`() {
        assertEquals(0.5f, SteamMath.progressFraction(50, 100), 1e-6f)
        assertEquals(1f, SteamMath.progressFraction(150, 100), 0f)
        assertEquals(0f, SteamMath.progressFraction(-5, 100), 0f)
        assertEquals(0f, SteamMath.progressFraction(10, 0), 0f)
        assertEquals(0f, SteamMath.progressFraction(10, -1), 0f)
    }

    @Test fun `needle sweeps 240 degrees from -120 to +120`() {
        assertEquals(-120f, SteamMath.needleAngleDeg(0f), 1e-4f)
        assertEquals(0f, SteamMath.needleAngleDeg(0.5f), 1e-4f)
        assertEquals(120f, SteamMath.needleAngleDeg(1f), 1e-4f)
        assertEquals(120f, SteamMath.needleAngleDeg(7f), 1e-4f)   // out of range is clamped
    }

    @Test fun `a touch maps onto the slide rule between its dead margins`() {
        assertEquals(0f, SteamMath.fractionAt(4f, 200f, 8f), 0f)      // in the left margin
        assertEquals(0f, SteamMath.fractionAt(8f, 200f, 8f), 0f)
        assertEquals(0.5f, SteamMath.fractionAt(100f, 200f, 8f), 1e-4f)
        assertEquals(1f, SteamMath.fractionAt(192f, 200f, 8f), 0f)
        assertEquals(1f, SteamMath.fractionAt(500f, 200f, 8f), 0f)    // past the right end
        assertEquals(0f, SteamMath.fractionAt(5f, 10f, 8f), 0f)       // degenerate width can't divide by zero
    }

    @Test fun `seek position is the fraction of the duration`() {
        assertEquals(30_000L, SteamMath.seekMs(0.5f, 60_000))
        assertEquals(60_000L, SteamMath.seekMs(1.4f, 60_000))
        assertEquals(0L, SteamMath.seekMs(-1f, 60_000))
        assertEquals(0L, SteamMath.seekMs(0.5f, 0))
    }
}

/** The art numbers in SteamArt are copied by hand from the generator; this fails if the two drift apart. */
class SteamArtMatchesGeneratorTest {
    private val regions: JSONObject by lazy {
        val f = listOf("../tools/keyart/steam_regions.json", "tools/keyart/steam_regions.json").map(::File).first { it.exists() }
        JSONObject(f.readText())
    }

    private fun ints(a: JSONArray) = (0 until a.length()).map { a.getInt(it) }

    private fun check(name: String, s: SteamArt.Slice) {
        val r = regions.getJSONObject(name)
        assertEquals("$name inset", ints(r.getJSONArray("inset")), listOf(s.left, s.top, s.right, s.bottom))
        assertEquals("$name outset", ints(r.getJSONArray("outset")), listOf(s.outL, s.outT, s.outR, s.outB))
    }

    @Test fun `nine-slice insets and outsets match steam_regions_json`() {
        check("panel", SteamArt.PANEL)
        check("plate", SteamArt.PLATE)
        check("window", SteamArt.WINDOW)
        check("tag", SteamArt.TAG)
        check("rail", SteamArt.RAIL)
        assertEquals(regions.getJSONObject("window").getInt("glass"), SteamArt.WINDOW_GLASS)
    }

    @Test fun `ring geometry matches steam_regions_json`() {
        val p = regions.getJSONObject("porthole")
        assertEquals(SteamArt.RING_CANVAS.toInt(), ints(p.getJSONArray("canvas"))[0])
        assertEquals(listOf(SteamArt.RING_CENTRE_X.toInt(), SteamArt.RING_CENTRE_Y.toInt()), ints(p.getJSONArray("centre")))
        assertEquals(SteamArt.PORTHOLE_HOLE.toInt(), p.getInt("hole"))
        val g = regions.getJSONObject("gauge")
        assertEquals(listOf(SteamArt.GAUGE_PIVOT_X.toInt(), SteamArt.GAUGE_PIVOT_Y.toInt()), ints(g.getJSONArray("pivot")))
        val w = regions.getJSONObject("wheel")
        assertEquals(SteamArt.WHEEL_CANVAS.toInt(), ints(w.getJSONArray("canvas"))[0])
        assertEquals(SteamArt.WHEEL_RIM.toInt(), w.getInt("rim"))
    }

    @Test fun `every 9-slice leaves a stretchable middle`() {
        for (n in listOf("panel", "plate", "window", "tag", "rail")) {
            val r = regions.getJSONObject(n)
            val (w, h) = ints(r.getJSONArray("canvas"))
            val (l, t, rr, b) = ints(r.getJSONArray("inset"))
            assertTrue("$n has no middle columns", w - l - rr > 0)
            assertTrue("$n has no middle rows", h - t - b > 0)
        }
    }
}
