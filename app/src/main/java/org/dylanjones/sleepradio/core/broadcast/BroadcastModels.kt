package org.dylanjones.sleepradio.core.broadcast

/** One track in the Broadcast rotation (from the SAF music folder). */
data class BroadcastTrack(
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
)

/** What the DJ says in the gap before a track (or nothing). */
enum class LinkKind { NONE, LINK, IDENT, TIME_CHECK }

/**
 * Wind-down stage, derived from the sleep timer:
 *  - [NORMAL]  no sleep timer, or plenty of time left — full cadence.
 *  - [EASING]  sleep timer running — links only, sparser and terser.
 *  - [SILENT]  near the end / fade — the DJ stops talking, music only.
 */
enum class WindDownPhase { NORMAL, EASING, SILENT }

/** How chatty the auto-DJ is. Maps to [BroadcastConfig.tracksPerLink]. */
enum class Chattiness(val id: String, val tracksPerLink: Int) {
    /** A link before every track: names what just played and what's next, no bare idents. */
    MAXIMUM("maximum", 1),
    CHATTY("chatty", 2),
    BALANCED("balanced", 3),
    MINIMAL("minimal", 5);

    companion object {
        val DEFAULT = BALANCED
        fun fromId(s: String?): Chattiness = entries.firstOrNull { it.id == s } ?: DEFAULT
    }
}

/**
 * Cadence of the auto-DJ. [tracksPerLink] comes from [Chattiness]; the ident /
 * time-check ratios are fixed for now.
 */
data class BroadcastConfig(
    /** A spoken link every N tracks. */
    val tracksPerLink: Int = Chattiness.DEFAULT.tracksPerLink,
    /** Every Mth link is a station ident instead of a track link. */
    val linksPerIdent: Int = 3,
    /** Every Kth link is a time check instead of a track link. */
    val linksPerTimeCheck: Int = 4,
    /**
     * Maximum chattiness: never drop to a bare station ident, and have time
     * checks still name the track that just played and the one coming up, so
     * every track is introduced and back-announced.
     */
    val announceEveryTrack: Boolean = false,
    /** DJ voice level, 0..1, multiplied by the master VOL. */
    val announcerVolume: Float = 1f,
    /** DJ speech rate: 1.0 = the voice's natural rate, higher is faster. */
    val announcerSpeed: Float = 1f,
    /** Drop a jingle in every N tracks (1..10); 0 = jingles off. */
    val jingleEvery: Int = 0,
)
