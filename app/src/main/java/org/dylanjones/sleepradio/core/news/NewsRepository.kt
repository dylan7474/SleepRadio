package org.dylanjones.sleepradio.core.news

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches the news feeds for a [NewsSlot] and picks what to read. Plain class, no
 * Hilt (same KSP2-avoidance as the other plain helpers). Makes network calls ONLY
 * when [headlinesFor] is called — with the news switch off nothing ever calls it.
 *
 * A 20-minute in-memory cache means a bulletin at :00 and one at :30 don't refetch a
 * feed both used, and remembers what's been read so the next bulletin gives fresh stories.
 * Every failure (offline, HTTP error, bad XML) yields "no stories from that feed" — never a throw.
 */
class NewsRepository(private val nowMs: () -> Long = System::currentTimeMillis) {

    private class Cached(val atMs: Long, val items: List<NewsHeadline>)

    private val cache = HashMap<String, Cached>()
    private val read = LinkedHashSet<String>()

    /** Up to [max] tidied headlines to read for [slot]; empty when offline or nothing fresh. */
    suspend fun headlinesFor(slot: NewsSlot, max: Int = 3): List<String> = withContext(Dispatchers.IO) {
        val all = NewsFeeds.forSlot(slot).flatMap { feed -> load(feed) }
        val picked = synchronized(read) { pickHeadlines(all, slot, read.toSet(), max) }
        Log.d(TAG, "news $slot: ${all.size} candidates -> ${picked.size} picked")
        picked
    }

    /** Remember these were read so the next bulletin skips them. */
    fun markRead(spoken: List<String>) = synchronized(read) {
        spoken.forEach { read += newsKey(it) }
        while (read.size > MAX_REMEMBERED) read.remove(read.first())
    }

    private fun load(feed: NewsFeed): List<NewsHeadline> {
        synchronized(cache) { cache[feed.url] }?.let { if (nowMs() - it.atMs < TTL_MS) return it.items }
        val body = fetch(feed.url) ?: return synchronized(cache) { cache[feed.url]?.items }.orEmpty()
        val items = runCatching { parseNewsRss(body, feed.name) }
            .onFailure { Log.w(TAG, "parse failed for ${feed.url}", it) }
            .getOrNull() ?: return emptyList()
        synchronized(cache) { cache[feed.url] = Cached(nowMs(), items) }
        return items
    }

    private fun fetch(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", UA)
                instanceFollowRedirects = true
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "fetch $url -> HTTP ${conn.responseCode}")
                return null
            }
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "fetch $url failed", e)
            null
        } finally {
            conn?.disconnect()
        }
    }

    private companion object {
        const val TAG = "NewsRepository"
        const val UA = "SleepRadio/1.0 (Android; +https://dylanjones.org)"
        const val TTL_MS = 20 * 60_000L
        const val MAX_REMEMBERED = 200
    }
}
