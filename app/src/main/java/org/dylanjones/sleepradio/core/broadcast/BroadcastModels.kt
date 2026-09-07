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
 * Cadence of the auto-DJ. Defaults are the Phase 9 Chunk C baseline; a Settings
 * screen wires these later (Chunk D), along with wind-down.
 */
data class BroadcastConfig(
    /** A spoken link every N tracks. */
    val tracksPerLink: Int = 3,
    /** Every Mth link is a station ident instead of a track link. */
    val linksPerIdent: Int = 3,
    /** Every Kth link is a time check instead of a track link. */
    val linksPerTimeCheck: Int = 4,
)
