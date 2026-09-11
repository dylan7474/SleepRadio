package org.dylanjones.sleepradio.core.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Online show directory backed by Apple's iTunes Search API — free, no key,
 * no account (Decision #20; holds the "keyless only" line from Decision
 * #9/#15). Plain object — no Hilt, no networking library; same
 * [HttpURLConnection] + org.json shape as [RadioDirectory].
 */
object PodcastDirectory {

    private const val TAG = "PodcastDirectory"
    private const val BASE = "https://itunes.apple.com/search"
    private const val UA = "SleepRadio/1.0 (Android; +https://dylanjones.org)"

    /** Shows whose title matches [query]. Empty on any error or blank query. */
    suspend fun search(query: String): List<PodcastFeed> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val encoded = URLEncoder.encode(q, "UTF-8")
        val url = URL("$BASE?media=podcast&entity=podcast&limit=30&term=$encoded")
        var conn: HttpURLConnection? = null
        try {
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "search '$q' -> HTTP ${conn.responseCode}")
                return@withContext emptyList()
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parse(body)
        } catch (e: Exception) {
            Log.w(TAG, "search '$q' failed", e)
            emptyList()
        } finally {
            conn?.disconnect()
        }
    }

    private fun parse(json: String): List<PodcastFeed> {
        val arr: JSONArray = JSONObject(json).optJSONArray("results") ?: JSONArray()
        val out = ArrayList<PodcastFeed>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val feedUrl = o.optString("feedUrl").trim()
            val title = o.optString("collectionName").ifBlank { o.optString("trackName") }.trim()
            if (feedUrl.isBlank() || title.isBlank()) continue
            val artwork = o.optString("artworkUrl600").ifBlank { o.optString("artworkUrl100") }.trim()
            out += PodcastFeed(
                id = podcastFeedId(feedUrl),
                feedUrl = feedUrl,
                title = title,
                artworkUrl = artwork.ifBlank { null },
            )
        }
        return out.distinctBy { it.feedUrl }
    }
}
