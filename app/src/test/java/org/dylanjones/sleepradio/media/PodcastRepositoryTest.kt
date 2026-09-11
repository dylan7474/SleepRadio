package org.dylanjones.sleepradio.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Phase 17 — the pure RSS-parsing helpers behind [PodcastRepository]. */
class PodcastRepositoryTest {

    // --- parseDuration ---------------------------------------------------

    @Test
    fun `duration as H-M-S`() {
        assertEquals(3_723_000L, parseDuration("1:02:03"))
    }

    @Test
    fun `duration as M-S`() {
        assertEquals(2_700_000L, parseDuration("45:00"))
    }

    @Test
    fun `duration as plain seconds`() {
        assertEquals(90_000L, parseDuration("90"))
    }

    @Test
    fun `duration blank or garbage is null`() {
        assertNull(parseDuration(null))
        assertNull(parseDuration(""))
        assertNull(parseDuration("not a duration"))
        assertNull(parseDuration("1:2:3:4"))
    }

    // --- parsePubDate ------------------------------------------------------

    @Test
    fun `pubDate parses RFC 1123`() {
        val ms = parsePubDate("Thu, 15 Jun 2023 07:00:00 GMT")
        assertEquals(Instant.parse("2023-06-15T07:00:00Z").toEpochMilli(), ms)
    }

    @Test
    fun `pubDate blank or garbage is null`() {
        assertNull(parsePubDate(null))
        assertNull(parsePubDate(""))
        assertNull(parsePubDate("not a date"))
    }

    // --- stripHtml -----------------------------------------------------------

    @Test
    fun `strips tags and unescapes entities`() {
        assertEquals(
            "Tea & biscuits, \"cosy\".",
            stripHtml("<p>Tea &amp; biscuits, &quot;cosy&quot;.</p>"),
        )
    }

    @Test
    fun `collapses whitespace left by stripped tags`() {
        assertEquals("one two", stripHtml("one<br/>  <br/>two"))
    }

    @Test
    fun `blank in is blank out`() {
        assertEquals("", stripHtml(""))
    }

    // --- parsePodcastRss -----------------------------------------------------

    private val sampleFeed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <title>Late Night Radio</title>
            <itunes:image href="https://example.com/art.jpg"/>
            <item>
              <title>Episode Two</title>
              <guid>ep-2</guid>
              <pubDate>Thu, 15 Jun 2023 07:00:00 GMT</pubDate>
              <itunes:duration>32:10</itunes:duration>
              <description>&lt;p&gt;The &amp;second&lt;/p&gt;</description>
              <enclosure url="https://example.com/ep2.mp3" type="audio/mpeg"/>
            </item>
            <item>
              <title>Episode One</title>
              <pubDate>Wed, 14 Jun 2023 07:00:00 GMT</pubDate>
              <enclosure url="https://example.com/ep1.mp3" type="audio/mpeg"/>
            </item>
            <item>
              <title>No enclosure — skipped</title>
              <pubDate>Tue, 13 Jun 2023 07:00:00 GMT</pubDate>
            </item>
            <item>
              <guid>no-title</guid>
              <enclosure url="https://example.com/notitle.mp3" type="audio/mpeg"/>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    @Test
    fun `parses feed title and itunes-image artwork`() {
        val parsed = parsePodcastRss("https://example.com/feed.xml", sampleFeed)
        assertEquals("Late Night Radio", parsed.feed.title)
        assertEquals("https://example.com/art.jpg", parsed.feed.artworkUrl)
    }

    @Test
    fun `skips items missing a title or an enclosure`() {
        val parsed = parsePodcastRss("https://example.com/feed.xml", sampleFeed)
        assertEquals(2, parsed.episodes.size)
        assertTrue(parsed.episodes.none { it.title.isBlank() })
        assertTrue(parsed.episodes.all { it.audioUrl.isNotBlank() })
    }

    @Test
    fun `episodes sort newest first`() {
        val parsed = parsePodcastRss("https://example.com/feed.xml", sampleFeed)
        assertEquals(listOf("Episode Two", "Episode One"), parsed.episodes.map { it.title })
    }

    @Test
    fun `guid falls back to the enclosure url when absent`() {
        val parsed = parsePodcastRss("https://example.com/feed.xml", sampleFeed)
        val ep1 = parsed.episodes.first { it.title == "Episode One" }
        assertEquals("https://example.com/ep1.mp3", ep1.guid)
    }

    @Test
    fun `description is html-stripped and duration parsed`() {
        val parsed = parsePodcastRss("https://example.com/feed.xml", sampleFeed)
        val ep2 = parsed.episodes.first { it.title == "Episode Two" }
        assertEquals("The &second", ep2.description)
        assertEquals(1_930_000L, ep2.durationMs)
    }

    @Test(expected = IllegalStateException::class)
    fun `a document with no channel throws`() {
        parsePodcastRss("https://example.com/feed.xml", "<rss version=\"2.0\"></rss>")
    }
}
