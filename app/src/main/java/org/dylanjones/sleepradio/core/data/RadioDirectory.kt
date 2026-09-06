package org.dylanjones.sleepradio.core.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Online station directory backed by the community Radio Browser API
 * (radio-browser.info). Plain object — no Hilt, no networking library; just
 * [HttpURLConnection] + org.json on the IO dispatcher.
 */
object RadioDirectory {

    private const val TAG = "RadioDirectory"
    // Round-robin DNS across the public mirrors.
    private const val BASE = "https://all.api.radio-browser.info"
    private const val UA = "SleepRadio/1.0 (Android; +https://dylanjones.org)"

    /** Stations whose name matches [query], best-known first. Empty on any error. */
    suspend fun search(query: String): List<RadioStation> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val encoded = URLEncoder.encode(q, "UTF-8")
        val url = URL(
            "$BASE/json/stations/search?name=$encoded&limit=40&hidebroken=true" +
                "&order=clickcount&reverse=true",
        )
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

    private fun parse(json: String): List<RadioStation> {
        val arr = JSONArray(json)
        val out = ArrayList<RadioStation>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val streamUrl = o.optString("url_resolved").ifBlank { o.optString("url") }.trim()
            val name = o.optString("name").trim().ifBlank { continue }
            if (streamUrl.isBlank()) continue
            val desc = listOfNotNull(
                o.optString("codec").takeIf { it.isNotBlank() && !it.equals("UNKNOWN", true) },
                o.optInt("bitrate").takeIf { it > 0 }?.let { "${it}k" },
                o.optString("country").trim().takeIf { it.isNotBlank() },
            ).joinToString(" · ").ifEmpty { "Internet radio" }
            out += RadioStation(
                id = "rb_" + o.optString("stationuuid").ifBlank { streamUrl.hashCode().toString() },
                name = name,
                streamUrl = streamUrl,
                description = desc,
            )
        }
        return out.distinctBy { it.streamUrl }
    }
}
