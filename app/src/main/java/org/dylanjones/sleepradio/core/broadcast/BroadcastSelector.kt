package org.dylanjones.sleepradio.core.broadcast

import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Picks the next Broadcast track, artist first. The next artist is chosen at
 * random but weighted by the square root of how many tracks they have (a big
 * catalogue is heard more often without taking over), and never one heard in
 * the last [ARTIST_GAP] picks (fewer when there are few artists). Then a track
 * from that artist's own shuffled bag: all their tracks play once before any
 * plays again, and a track isn't replayed within [noRepeat] picks. Artists
 * match ignoring case and a leading "The". Deterministic when given a seeded
 * [rng] (for tests). Nothing is stored: every start is a fresh shuffle.
 *
 * Plain class, no Hilt.
 */
class BroadcastSelector(
    pool: List<BroadcastTrack>,
    private val rng: Random = Random.Default,
) {
    private val tracks = pool.distinctBy { it.uri }
    private val groups: Map<String, List<String>> =
        tracks.groupBy({ artistKey(it.artist) }, { it.uri })
    private val byUri = tracks.associateBy { it.uri }
    private val weight = groups.mapValues { sqrt(it.value.size.toDouble()) }
    private val bags = HashMap<String, MutableList<String>>()
    private val recentUris = ArrayDeque<String>()
    private val recentArtists = ArrayDeque<String>()

    /** Don't replay a track until this many others have gone by. */
    private val noRepeat = (tracks.size / 2).coerceIn(1, 20)

    fun hasTracks(): Boolean = tracks.isNotEmpty()

    fun next(): BroadcastTrack? {
        if (tracks.isEmpty()) return null
        val gap = minOf(ARTIST_GAP, groups.size / 2)
        val heard = recentArtists.toList().takeLast(gap)
        val all = groups.keys.toList()
        val artists = all.filter { it !in heard && hasFreshTrack(it) }
            .ifEmpty { all.filter { it !in heard } }
            .ifEmpty { all }
        val artist = pickWeighted(artists)

        val bag = bags[artist]?.takeIf { it.isNotEmpty() }
            ?: groups.getValue(artist).shuffled(rng).toMutableList().also { bags[artist] = it }
        val i = bag.indexOfFirst { it !in recentUris }.coerceAtLeast(0)   // (not one just played)
        val uri = bag.removeAt(i)

        recentUris.addLast(uri)
        while (recentUris.size > noRepeat) recentUris.removeFirst()
        if (artist.isNotEmpty()) {                 // (tracks with no artist tag aren't spaced out)
            recentArtists.addLast(artist)
            while (recentArtists.size > ARTIST_GAP) recentArtists.removeFirst()
        }
        return byUri.getValue(uri)
    }

    private fun hasFreshTrack(artist: String): Boolean {
        val bag = bags[artist]?.takeIf { it.isNotEmpty() } ?: groups.getValue(artist)
        return bag.any { it !in recentUris }
    }

    private fun pickWeighted(artists: List<String>): String {
        var r = rng.nextDouble() * artists.sumOf { weight.getValue(it) }
        for (a in artists) {
            r -= weight.getValue(a)
            if (r < 0) return a
        }
        return artists.last()
    }

    private companion object {
        const val ARTIST_GAP = 5

        /** "The Beatles", "Beatles", "beatles " -> "beatles". */
        fun artistKey(artist: String): String {
            val key = artist.lowercase().trim().split(Regex("\\s+")).joinToString(" ")
            return if (key.startsWith("the ") && key.length > 4) key.substring(4) else key
        }
    }
}
