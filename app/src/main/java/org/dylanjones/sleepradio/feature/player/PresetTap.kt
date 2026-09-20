package org.dylanjones.sleepradio.feature.player

import org.dylanjones.sleepradio.core.data.PRESETS_PER_PAGE
import org.dylanjones.sleepradio.core.data.SourceSlot
import org.dylanjones.sleepradio.core.data.SourceType
import org.dylanjones.sleepradio.playback.PlaybackState

/** Which page of the channel pager holds preset [index] (0-based). */
internal fun presetPage(index: Int): Int = index / PRESETS_PER_PAGE

/** What tapping a preset button does. */
internal enum class PresetTap {
    /** Empty slot: open the source picker for it. */
    PICK_SOURCE,

    /** Nothing sensible to do (a Broadcast that is still starting up). */
    IGNORE,

    /** The preset is the source that is loaded: pause it if it is playing, resume it if not. */
    TOGGLE_PLAYBACK,

    /** A different (or not yet loaded) source: start it. */
    PLAY,
}

/**
 * True when [slot] is the source currently loaded in the player — the one the preset row shows
 * as active. Matching [nowPlayingRef] alone isn't enough: it lingers after a Broadcast has been
 * ended (e.g. by the sleep timer), when tapping the preset must start it afresh, not "resume"
 * nothing.
 */
internal fun isActivePreset(slot: SourceSlot?, nowPlayingRef: String?, pb: PlaybackState): Boolean =
    slot != null && slot.refId == nowPlayingRef && hasLoadedSource(pb)

/** True when the player has a source to play or pause — a Broadcast, a radio stream or a queue. */
internal fun hasLoadedSource(pb: PlaybackState): Boolean =
    pb.isConnected && (pb.isBroadcast || pb.isRadio || pb.queueSize > 0)

/** True when the loaded source is audibly running or about to be (buffering, or a Broadcast tuning in). */
internal fun isPresetPlaying(pb: PlaybackState, broadcastStarting: Boolean): Boolean =
    pb.isPlaying || pb.isBuffering || broadcastStarting

internal fun presetTapAction(
    slot: SourceSlot?,
    nowPlayingRef: String?,
    pb: PlaybackState,
    broadcastStarting: Boolean,
): PresetTap = when {
    slot == null -> PresetTap.PICK_SOURCE
    // A second tap while the Broadcast is still tuning in would start it twice.
    slot.type == SourceType.BROADCAST && broadcastStarting -> PresetTap.IGNORE
    isActivePreset(slot, nowPlayingRef, pb) -> PresetTap.TOGGLE_PLAYBACK
    else -> PresetTap.PLAY
}
