package org.dylanjones.sleepradio.core.news

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NewsTextTest {

    private fun h(title: String, source: String = "A", at: Long? = null, summary: String = "") =
        NewsHeadline(title, summary, at, source)

    // --- RSS parsing (synthetic feed, not real headlines) ---

    @Test
    fun `parses items in order and skips untitled ones`() {
        val xml = """<?xml version="1.0"?><rss version="2.0"><channel><title>T</title>
            <item><title>Council opens new riverside walkway</title>
              <description>&lt;p&gt;The path is &lt;b&gt;two miles&lt;/b&gt; long.&lt;/p&gt;</description>
              <pubDate>Sun, 20 Sep 2026 09:00:00 GMT</pubDate></item>
            <item><title>   </title></item>
            <item><title>Museum finds a lost painting</title></item>
            </channel></rss>"""
        val items = parseNewsRss(xml, "Feed")
        assertEquals(2, items.size)
        assertEquals("Council opens new riverside walkway", items[0].title)
        assertEquals("The path is two miles long.", items[0].summary)
        assertTrue(items[0].pubDateMs != null)
        assertEquals("Feed", items[0].source)
    }

    @Test(expected = IllegalStateException::class)
    fun `channel-less document is a failure`() {
        parseNewsRss("<rss></rss>", "Feed")
    }

    @Test(expected = Exception::class)
    fun `refuses a DOCTYPE`() {
        parseNewsRss("""<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><rss><channel><item><title>&e;</title></item></channel></rss>""", "F")
    }

    // --- tidying ---

    @Test
    fun `strips web-page labels and adds a full stop`() {
        assertEquals("Museum finds a lost painting in the attic.", tidyHeadline("Watch: Museum finds a lost painting in the attic"))
        assertEquals("Village hall reopens after repairs.", tidyHeadline("LIVE - Village hall reopens after repairs"))
    }

    @Test
    fun `turns a spaced dash into a pause`() {
        assertEquals("Baking show returns, host on the new series.", tidyHeadline("Baking show returns - host on the new series"))
    }

    @Test
    fun `rejects unspeakable stories and bad lengths`() {
        assertNull(tidyHeadline("Live updates: storm crosses the country"))
        assertNull(tidyHeadline("Quiz: how well do you know the river?"))
        assertNull(tidyHeadline("Short one"))
        assertNull(tidyHeadline("x".repeat(200)))
    }

    @Test
    fun `bare topic labels are not stories`() {
        assertNull(tidyHeadline("Golf: PGA Championship"))
        assertNull(tidyHeadline("Tennis: US Open day two."))
        assertNull(tidyHeadline("Cricket: England v India"))
    }

    @Test
    fun `colon headlines that are real stories still pass`() {
        assertEquals(
            "'People forget we exist': Why older LGBTQ+ people fear losing identity.",
            tidyHeadline("'People forget we exist': Why older LGBTQ+ people fear losing identity"),
        )
        assertEquals(
            "The Deadlifting Grandma: 'I'm a world champion'.",
            tidyHeadline("The Deadlifting Grandma: 'I'm a world champion'"),
        )
        assertEquals(
            "Assisted dying: How laws differ around the world.",
            tidyHeadline("Assisted dying: How laws differ around the world"),
        )
        assertEquals(
            "Menstrual fitness: Why some women are 'cycle-syncing' their workouts.",
            tidyHeadline("Menstrual fitness: Why some women are 'cycle-syncing' their workouts"),
        )
    }

    @Test
    fun `promos for video and iPlayer are dropped`() {
        assertNull(tidyHeadline("Watch the full interview with Earl Spencer on iPlayer in Princess Diana: My Sister's Story"))
        assertNull(tidyHeadline("Listen live to the debate on BBC Sounds tonight"))
    }

    // --- grim filter ---

    @Test
    fun `grim words are caught as whole words only`() {
        assertTrue(isGrim("Two people died in the crash"))
        assertTrue(isGrim("Drone attack on the capital"))
        assertFalse(isGrim("Warm weather boosts warmth of garden"))   // 'war' inside 'warm' must not match
        assertFalse(isGrim("Bakery wins award for best bread"))
    }

    // --- picking ---

    @Test
    fun `soft slot skips grim stories but top slot keeps them`() {
        val items = listOf(
            h("Two people died in the storm overnight"),
            h("Library reopens after a long refurbishment"),
        )
        assertEquals(listOf("Library reopens after a long refurbishment."), pickHeadlines(items, NewsSlot.HALF_PAST))
        assertEquals(2, pickHeadlines(items, NewsSlot.TOP_OF_HOUR).size)
    }

    @Test
    fun `grim summary also blocks a soft story`() {
        val items = listOf(h("Local choir wins regional prize today", summary = "A tragic bombing nearly cancelled it."))
        assertTrue(pickHeadlines(items, NewsSlot.HALF_PAST).isEmpty())
    }

    @Test
    fun `newest first within a feed and feeds interleaved`() {
        val items = listOf(
            h("Alpha story that is older than the rest", "A", at = 1),
            h("Alpha story that is the newest one here", "A", at = 9),
            h("Beta story published in the middle time", "B", at = 5),
        )
        val picked = pickHeadlines(items, NewsSlot.TOP_OF_HOUR, max = 3)
        assertEquals(
            listOf(
                "Alpha story that is the newest one here.",
                "Beta story published in the middle time.",
                "Alpha story that is older than the rest.",
            ),
            picked,
        )
    }

    @Test
    fun `skips already-read stories and near-duplicates`() {
        val items = listOf(
            h("Harbour festival draws record crowds this weekend"),
            h("Harbour festival draws record crowds this year, organisers say"), // same first six words
            h("New bus timetable starts on Monday morning"),
        )
        val first = pickHeadlines(items, NewsSlot.TOP_OF_HOUR)
        assertEquals(2, first.size)
        val read = first.map(::newsKey).toSet()
        assertTrue(pickHeadlines(items, NewsSlot.TOP_OF_HOUR, alreadyRead = read).isEmpty())
    }

    @Test
    fun `respects max`() {
        val items = List(10) { h("Distinct story number ${'a' + it} about something else entirely") }
        assertEquals(3, pickHeadlines(items, NewsSlot.TOP_OF_HOUR).size)
        assertEquals(5, pickHeadlines(items, NewsSlot.TOP_OF_HOUR, max = 5).size)
    }

    // --- bulletin ---

    @Test
    fun `empty headlines means no bulletin`() {
        assertNull(buildBulletin(NewsSlot.TOP_OF_HOUR, emptyList(), "It's ten o'clock."))
    }

    @Test
    fun `bulletin is time then intro then stories then outro`() {
        val b = buildBulletin(NewsSlot.HALF_PAST, listOf("Library reopens."), "It's half past ten.", Random(0))!!
        assertTrue(b, b.startsWith("It's half past ten. "))
        assertTrue(b, b.contains("Library reopens."))
        assertTrue(b, b.endsWith("Now, back to the music."))
        assertTrue(b, listOf("gentler", "softer", "lighter").any { b.contains(it) })
    }

    @Test
    fun `numbers in headlines are made speakable`() {
        val b = buildBulletin(NewsSlot.TOP_OF_HOUR, listOf("Bridge reopens after 12 weeks."), "It's ten o'clock.")!!
        assertFalse(b, b.any { it.isDigit() })
    }

    // --- speakable numbers ---

    @Test
    fun `money is spelled out`() {
        assertEquals("New one million pounds grant.", speakableNews("New £1m grant."))
        assertEquals("two point five billion pounds", speakableNews("£2.5bn"))
        assertEquals("four hundred and fifty pounds", speakableNews("£450"))
        assertEquals("one pound", speakableNews("£1"))
        assertEquals("three million dollars", speakableNews("$3 million"))
    }

    @Test
    fun `percent pence grouped numbers and symbols`() {
        assertEquals("Prices up five percent", speakableNews("Prices up 5%"))
        assertEquals("ten pence cut to fuel duty", speakableNews("10p cut to fuel duty"))
        assertEquals("twelve thousand people", speakableNews("12,000 people"))
        assertEquals("older LGBTQ people", speakableNews("older LGBTQ+ people"))
        assertEquals("Smith and Sons", speakableNews("Smith & Sons"))
    }

    @Test
    fun `a bulletin never contains digits or currency symbols`() {
        val b = buildBulletin(
            NewsSlot.TOP_OF_HOUR,
            listOf("New £1m grant, 5% rise and a 10p cut for 12,000 people in 2026."),
            "It's ten o'clock.",
        )!!
        assertFalse(b, b.any { it.isDigit() || it in "£$€%" })
    }

    // --- signposts between stories ---

    @Test
    fun `a lone story has no signpost`() {
        val b = buildBulletinBody(NewsSlot.TOP_OF_HOUR, listOf("Library reopens."), Random(0))!!
        assertFalse(b, b.contains("First up") || b.contains("And finally"))
    }

    @Test
    fun `two stories are first up then and finally`() {
        val b = buildBulletinBody(NewsSlot.TOP_OF_HOUR, listOf("Alpha.", "Beta."), Random(0))!!
        assertTrue(b, b.contains("First up, Alpha. And finally, Beta."))
    }

    @Test
    fun `middle stories are signposted, in order, without repeats`() {
        for (seed in 0..30) {
            val out = signpost(listOf("A one.", "B two.", "C three.", "D four.", "E five."), Random(seed))
            assertTrue(out, out.startsWith("First up, A one."))
            assertTrue(out, out.endsWith("And finally, E five."))
            val leads = Regex("(Also,|And also,|Meanwhile,|In other news,|Elsewhere,) [B-D] ")
                .findAll(out).map { it.groupValues[1] }.toList()
            assertEquals(out, 3, leads.size)
            assertEquals(out, 3, leads.toSet().size)
            assertTrue(out, out.indexOf("B two") < out.indexOf("C three") && out.indexOf("C three") < out.indexOf("D four"))
        }
    }
}
