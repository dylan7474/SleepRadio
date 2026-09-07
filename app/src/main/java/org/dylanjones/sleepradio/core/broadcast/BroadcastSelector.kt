package org.dylanjones.sleepradio.core.broadcast

import kotlin.random.Random

/**
 * Picks the next Broadcast track: a weighted shuffle that avoids repeating a
 * track within the last [noRepeat] picks and spaces the same artist out by
 * [ARTIST_SPACING]. Deterministic when given a seeded [rng] (for tests).
 *
 * Plain class, no Hilt.
 */
class BroadcastSelector(
    private val pool: List<BroadcastTrack>,
    private val rng: Random = Random.Default,
) {
    private val recentUris = ArrayDeque<String>()
    private val recentArtists = ArrayDeque<String>()

    /** Don't replay a track until this many others have gone by. */
    private val noRepeat = (pool.size / 2).coerceIn(1, 20)

    fun hasTracks(): Boolean = pool.isNotEmpty()

    fun next(): BroadcastTrack? {
        if (pool.isEmpty()) return null
        val candidates = pool
            .filter { it.uri !in recentUris && it.artist.lowercase() !in recentArtists }
            .ifEmpty { pool.filter { it.uri !in recentUris } }
            .ifEmpty { pool }
        val pick = candidates[rng.nextInt(candidates.size)]

        recentUris.addLast(pick.uri)
        while (recentUris.size > noRepeat) recentUris.removeFirst()
        if (pick.artist.isNotBlank()) {
            recentArtists.addLast(pick.artist.lowercase())
            while (recentArtists.size > ARTIST_SPACING) recentArtists.removeFirst()
        }
        return pick
    }

    private companion object {
        const val ARTIST_SPACING = 2
    }
}
