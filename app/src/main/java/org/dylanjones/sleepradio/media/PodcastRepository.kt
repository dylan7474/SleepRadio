package org.dylanjones.sleepradio.media

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dylanjones.sleepradio.core.data.PodcastEpisode
import org.dylanjones.sleepradio.core.data.PodcastFeed
import org.dylanjones.sleepradio.core.data.podcastFeedId
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory

/** [PodcastRepository.load]'s result: the feed's own metadata (title/artwork,
 *  which can differ slightly from a directory search result) plus its
 *  episodes, newest first. */
data class ParsedPodcastFeed(val feed: PodcastFeed, val episodes: List<PodcastEpisode>)

/**
 * Reads a podcast's own RSS feed — the feed *is* the source of truth for its
 * episode list (Phase 17, Decision #20). Plain [HttpURLConnection] + the JDK's
 * built-in DOM parser; no networking or XML library, same philosophy as
 * [org.dylanjones.sleepradio.core.data.RadioDirectory]. Episodes are never
 * persisted — an in-memory cache just avoids re-fetching on back-navigation.
 */
@Singleton
class PodcastRepository @Inject constructor() {

    private companion object {
        const val TAG = "PodcastRepository"
        const val UA = "SleepRadio/1.0 (Android; +https://dylanjones.org)"
    }

    private val cache = LinkedHashMap<String, ParsedPodcastFeed>()

    /** Fetch + parse [feedUrl]. Uses the in-memory cache unless [refresh]. Null
     *  on any network or parse failure (bad URL, not RSS, malformed XML). */
    suspend fun load(feedUrl: String, refresh: Boolean = false): ParsedPodcastFeed? =
        withContext(Dispatchers.IO) {
            if (!refresh) synchronized(cache) { cache[feedUrl] }?.let { return@withContext it }
            val body = fetch(feedUrl) ?: return@withContext null
            val parsed = runCatching { parsePodcastRss(feedUrl, body) }.getOrElse {
                Log.w(TAG, "parse failed for $feedUrl", it)
                null
            } ?: return@withContext null
            synchronized(cache) { cache[feedUrl] = parsed }
            parsed
        }

    private fun fetch(feedUrl: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(feedUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", UA)
                instanceFollowRedirects = true
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "fetch $feedUrl -> HTTP ${conn.responseCode}")
                return null
            }
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "fetch $feedUrl failed", e)
            null
        } finally {
            conn?.disconnect()
        }
    }
}

// --- Pure RSS parsing (no Android dependency — testable on the host JVM) --

/**
 * Parse a podcast RSS/Atom-with-enclosures document into its show metadata +
 * episode list. Deliberately not namespace-aware — `itunes:duration` etc. are
 * matched by their literal qualified tag name, which is simpler and good
 * enough for the handful of `itunes:*` tags real-world feeds use. An episode
 * missing a title or an audio enclosure is skipped rather than failing the
 * whole feed; a channel-less document throws (caller treats it as a failure).
 */
internal fun parsePodcastRss(feedUrl: String, xml: String): ParsedPodcastFeed {
    // Android's on-device DocumentBuilderFactory (a different implementation
    // from the JDK's Xerces used by host-JVM unit tests) throws
    // UnsupportedOperationException on setXIncludeAware/setExpandEntityReferences
    // rather than just ignoring them — both already default to false/true-safe,
    // so guard every optional knob with runCatching instead of the plain
    // `.apply` this used before device testing caught it.
    val factory = DocumentBuilderFactory.newInstance()
    runCatching { factory.isNamespaceAware = false }
    runCatching { factory.isXIncludeAware = false }
    runCatching { factory.isExpandEntityReferences = false }
    // Feeds are third-party, untrusted XML — refuse a DOCTYPE / external
    // entities outright rather than merely disabling expansion above, so a
    // malicious feed can't attempt XXE (local file read / SSRF via entities).
    listOf(
        "http://apache.org/xml/features/disallow-doctype-decl" to true,
        "http://xml.org/sax/features/external-general-entities" to false,
        "http://xml.org/sax/features/external-parameter-entities" to false,
        "http://xml.org/sax/features/load-external-dtd" to false,
    ).forEach { (name, value) -> runCatching { factory.setFeature(name, value) } }

    val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
    val channel = doc.getElementsByTagName("channel").item(0) as? Element
        ?: error("no <channel> in feed")

    val title = channel.child("title")?.text()?.ifBlank { null } ?: feedUrl
    val artwork = channel.child("itunes:image")?.getAttribute("href")?.trim()?.ifBlank { null }
        ?: channel.child("image")?.child("url")?.text()?.ifBlank { null }

    val episodes = channel.children("item").mapNotNull { item ->
        val audioUrl = item.child("enclosure")?.getAttribute("url")?.trim().orEmpty()
        val epTitle = item.child("title")?.text().orEmpty()
        if (audioUrl.isBlank() || epTitle.isBlank()) return@mapNotNull null
        PodcastEpisode(
            guid = item.child("guid")?.text()?.ifBlank { null } ?: audioUrl,
            title = epTitle,
            audioUrl = audioUrl,
            pubDateMs = parsePubDate(item.child("pubDate")?.text()),
            durationMs = parseDuration(item.child("itunes:duration")?.text()),
            description = stripHtml(
                item.child("description")?.text() ?: item.child("itunes:summary")?.text().orEmpty(),
            ),
        )
    }
    return ParsedPodcastFeed(
        feed = PodcastFeed(id = podcastFeedId(feedUrl), feedUrl = feedUrl, title = title, artworkUrl = artwork),
        episodes = episodes.sortedByDescending { it.pubDateMs ?: 0L },
    )
}

/** The direct child element named [tag] (not a descendant), or null. */
private fun Element.child(tag: String): Element? {
    val kids = childNodes
    for (i in 0 until kids.length) {
        val n = kids.item(i)
        if (n is Element && n.tagName == tag) return n
    }
    return null
}

/** All direct child elements named [tag], in document order. */
private fun Element.children(tag: String): List<Element> {
    val out = ArrayList<Element>()
    val kids = childNodes
    for (i in 0 until kids.length) {
        val n = kids.item(i)
        if (n is Element && n.tagName == tag) out += n
    }
    return out
}

private fun Element.text(): String = (textContent ?: "").trim()

/** RSS `pubDate` (RFC 822 / RFC 1123, e.g. "Wed, 15 Jun 2023 07:00:00 GMT") ->
 *  epoch ms. Null if blank or unparseable — the episode just sorts last. */
internal fun parsePubDate(raw: String?): Long? {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching {
        Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(text)).toEpochMilli()
    }.getOrNull()
}

/** `itunes:duration` as "HH:MM:SS", "MM:SS" or plain seconds -> ms. Null if
 *  blank or unparseable. */
internal fun parseDuration(raw: String?): Long? {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val parts = text.split(":").map { it.toIntOrNull() ?: return null }
    if (parts.isEmpty() || parts.size > 3) return null
    val seconds = when (parts.size) {
        1 -> parts[0]
        2 -> parts[0] * 60 + parts[1]
        else -> parts[0] * 3600 + parts[1] * 60 + parts[2]
    }
    return seconds * 1000L
}

/** Strip markup from an episode description for plain-text display. Hand-
 *  rolled (no [android.text.Html], which isn't available in host-JVM unit
 *  tests) — good enough for the simple HTML real feeds use, not a full
 *  sanitiser. */
internal fun stripHtml(raw: String): String {
    if (raw.isBlank()) return ""
    return raw
        .replace(Regex("<[^>]*>"), " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace(Regex("\\s+"), " ")
        .trim()
}
