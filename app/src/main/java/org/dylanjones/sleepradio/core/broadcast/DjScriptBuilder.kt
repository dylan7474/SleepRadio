package org.dylanjones.sleepradio.core.broadcast

import java.time.LocalTime
import kotlin.random.Random

/**
 * Decides *when* the DJ speaks and *what kind* of link, from [BroadcastConfig].
 * Call [onTrackStarted] once each time a track begins; it returns the link to
 * play in the gap **after** that track.
 *
 * Plain class, no Hilt. Not thread-safe — driven from the single playback thread.
 */
class ShowClock(private val config: BroadcastConfig) {
    private var tracksSinceLink = 0
    private var linkCount = 0

    fun onTrackStarted(): LinkKind {
        tracksSinceLink++
        if (tracksSinceLink < config.tracksPerLink) return LinkKind.NONE
        tracksSinceLink = 0
        linkCount++
        return when {
            linkCount % config.linksPerIdent == 0 -> LinkKind.IDENT
            linkCount % config.linksPerTimeCheck == 0 -> LinkKind.TIME_CHECK
            else -> LinkKind.LINK
        }
    }

    fun reset() {
        tracksSinceLink = 0
        linkCount = 0
    }
}

/**
 * Builds the DJ's spoken links from templates. Deterministic with a seeded
 * [rng]. Kept plain text (no SSML) — sherpa/Piper reads it as-is.
 */
class DjScriptBuilder(private val rng: Random = Random.Default) {

    fun build(
        kind: LinkKind,
        previous: BroadcastTrack?,
        next: BroadcastTrack?,
        now: LocalTime = LocalTime.now(),
    ): String = when (kind) {
        LinkKind.NONE -> ""
        LinkKind.IDENT -> IDENTS.random(rng)
        LinkKind.TIME_CHECK -> {
            val time = spokenTime(now)
            val tail = next?.let { " Here's ${trackPhrase(it)}." }.orEmpty()
            "${TIME_LEADS.random(rng)} $time.$tail"
        }
        LinkKind.LINK -> {
            val outro = previous?.let { "${OUTROS.random(rng)} ${trackPhrase(it)}. " }.orEmpty()
            val intro = next?.let { "${INTROS.random(rng)} ${trackPhrase(it)}." }
                ?: STATION_ONLY.random(rng)
            (outro + intro).trim()
        }
    }

    /** "Song by Artist", or just the title when the artist is unknown/blank/==title. */
    private fun trackPhrase(t: BroadcastTrack): String {
        val title = t.title.trim().ifEmpty { "that one" }
        val artist = t.artist.trim()
        val hideArtist = artist.isEmpty() ||
            artist.equals("unknown", ignoreCase = true) ||
            artist.equals(title, ignoreCase = true)
        return if (hideArtist) title else "$title, by $artist"
    }

    private companion object {
        val IDENTS = listOf(
            "You're listening to Sleep Radio.",
            "This is Sleep Radio — music through the night.",
            "Sleep Radio. Stay with us.",
        )
        val OUTROS = listOf("That was", "You just heard", "Before that,")
        val INTROS = listOf("Coming up,", "Here's", "Next,", "Let's stay with")
        val TIME_LEADS = listOf("It's coming up to", "The time is", "It's just gone")
        val STATION_ONLY = listOf(
            "You're with Sleep Radio.",
            "More music in a moment.",
        )
    }
}

/** A loose, TTS-friendly spoken form of the time — no digits. */
internal fun spokenTime(t: LocalTime): String {
    val m = t.minute
    val roundedTo5 = ((m + 2) / 5) * 5
    var hour = t.hour
    var mins = roundedTo5
    if (mins == 60) {
        mins = 0
        hour = (hour + 1) % 24
    }
    val h12 = ((hour + 11) % 12) + 1
    val hourWord = NUMBER_WORDS[h12]
    val nextHourWord = NUMBER_WORDS[((h12) % 12) + 1]
    return when (mins) {
        0 -> when (hour) {
            0 -> "midnight"
            12 -> "midday"
            else -> "$hourWord o'clock"
        }
        15 -> "quarter past $hourWord"
        30 -> "half past $hourWord"
        45 -> "quarter to $nextHourWord"
        else -> if (mins < 30) {
            "${NUMBER_WORDS[mins]} minutes past $hourWord"
        } else {
            "${NUMBER_WORDS[60 - mins]} minutes to $nextHourWord"
        }
    }
}

private val NUMBER_WORDS: Map<Int, String> = mapOf(
    1 to "one", 2 to "two", 3 to "three", 4 to "four", 5 to "five",
    6 to "six", 7 to "seven", 8 to "eight", 9 to "nine", 10 to "ten",
    11 to "eleven", 12 to "twelve", 13 to "thirteen", 14 to "fourteen",
    15 to "fifteen", 20 to "twenty", 25 to "twenty-five",
)

private fun <T> List<T>.random(rng: Random): T = this[rng.nextInt(size)]
