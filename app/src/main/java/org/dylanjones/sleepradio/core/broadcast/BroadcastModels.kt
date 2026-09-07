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
)
