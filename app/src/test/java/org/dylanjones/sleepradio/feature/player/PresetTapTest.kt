package org.dylanjones.sleepradio.feature.player

import org.dylanjones.sleepradio.core.data.SourceSlot
import org.dylanjones.sleepradio.core.data.SourceType
import org.dylanjones.sleepradio.playback.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetTapTest {

    private fun slot(type: SourceType, ref: String, index: Int = 0) =
        SourceSlot(index = index, type = type, refId = ref, label = ref, sublabel = "")

    private val radio = slot(SourceType.RADIO, "http://radio")
    private val book = slot(SourceType.AUDIOBOOK, "book1", index = 2)
    private val broadcast = slot(SourceType.BROADCAST, "broadcast", index = 1)

    private val idle = PlaybackState(isConnected = true)
    private val radioPlaying = PlaybackState(isConnected = true, isRadio = true, isPlaying = true)
    private val radioPaused = PlaybackState(isConnected = true, isRadio = true, isPlaying = false)
    private val onAir = PlaybackState(isConnected = true, isBroadcast = true, isPlaying = true)

    @Test fun `an empty slot opens the source picker`() =
        assertEquals(PresetTap.PICK_SOURCE, presetTapAction(null, null, idle, false))

    @Test fun `tapping the playing preset pauses it rather than restarting`() =
        assertEquals(PresetTap.TOGGLE_PLAYBACK, presetTapAction(radio, radio.refId, radioPlaying, false))

    @Test fun `tapping the paused preset resumes it`() =
        assertEquals(PresetTap.TOGGLE_PLAYBACK, presetTapAction(radio, radio.refId, radioPaused, false))

    @Test fun `tapping a different preset switches to it`() =
        assertEquals(PresetTap.PLAY, presetTapAction(book, radio.refId, radioPlaying, false))

    @Test fun `the Broadcast on air toggles, never restarts with a fresh welcome`() =
        assertEquals(PresetTap.TOGGLE_PLAYBACK, presetTapAction(broadcast, broadcast.refId, onAir, false))

    @Test fun `a second tap while the Broadcast is tuning in is ignored`() =
        assertEquals(PresetTap.IGNORE, presetTapAction(broadcast, null, idle, broadcastStarting = true))

    @Test fun `a lingering now-playing ref with nothing loaded starts the source afresh`() {
        // e.g. the sleep timer ended the Broadcast: nowPlayingRef still matches, but no source is loaded.
        assertEquals(PresetTap.PLAY, presetTapAction(broadcast, broadcast.refId, idle, false))
        assertEquals(PresetTap.PLAY, presetTapAction(radio, radio.refId, PlaybackState(isConnected = false), false))
    }

    @Test fun `a loaded album counts as active by its queue`() {
        val album = slot(SourceType.MUSIC_FOLDER, "album")
        val playingAlbum = PlaybackState(isConnected = true, queueSize = 12, isPlaying = true)
        assertEquals(PresetTap.TOGGLE_PLAYBACK, presetTapAction(album, album.refId, playingAlbum, false))
    }

    @Test fun `only the loaded preset is shown active`() {
        assertTrue(isActivePreset(radio, radio.refId, radioPlaying))
        assertFalse(isActivePreset(book, radio.refId, radioPlaying))
        assertFalse(isActivePreset(null, radio.refId, radioPlaying))
    }

    @Test fun `playing means running, buffering or tuning in`() {
        assertTrue(isPresetPlaying(radioPlaying, false))
        assertTrue(isPresetPlaying(PlaybackState(isConnected = true, isBuffering = true), false))
        assertTrue(isPresetPlaying(idle, broadcastStarting = true))
        assertFalse(isPresetPlaying(radioPaused, false))
    }

    @Test fun `something is loaded when connected with a broadcast, a radio stream or a queue`() {
        assertTrue(hasLoadedSource(onAir))
        assertTrue(hasLoadedSource(radioPaused))
        assertTrue(hasLoadedSource(PlaybackState(isConnected = true, queueSize = 3)))
        assertFalse(hasLoadedSource(idle))
        assertFalse(hasLoadedSource(PlaybackState(isConnected = false, isRadio = true)))
    }

    @Test fun `channels 1-4 are page 0 and 5-8 are page 1`() {
        assertEquals(listOf(0, 0, 0, 0, 1, 1, 1, 1), (0 until 8).map(::presetPage))
        assertEquals(8, org.dylanjones.sleepradio.core.data.PRESET_COUNT)
    }
}
