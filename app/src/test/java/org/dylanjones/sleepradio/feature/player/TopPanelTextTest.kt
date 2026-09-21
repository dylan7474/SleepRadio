package org.dylanjones.sleepradio.feature.player

import org.dylanjones.sleepradio.playback.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TopPanelTextTest {
    private val music = PlaybackState(isConnected = true, artist = "Bob Dylan", title = "Black Crow Blues")

    @Test fun `music shows artist and track`() =
        assertEquals(Readouts("Artist", "Bob Dylan", "Track", "Black Crow Blues"), readoutsFor(music, false))

    @Test fun `an empty music state shows dashes, never blank windows`() {
        val r = readoutsFor(PlaybackState(isConnected = true), false)
        assertEquals("—", r.value1)
        assertEquals("—", r.value2)
    }

    @Test fun `radio shows the station and what is playing, falling back to Live`() {
        val r = PlaybackState(isRadio = true, stationName = "BBC Radio 4", nowPlaying = "The World at One")
        assertEquals(Readouts("Station", "BBC Radio 4", "Now playing", "The World at One"), readoutsFor(r, false))
        assertEquals("Live", readoutsFor(PlaybackState(isRadio = true, stationName = "X"), false).value2)
    }

    @Test fun `books and podcasts use their own labels`() {
        assertEquals(
            Readouts("Book", "Bill Bryson", "Chapter", "Ch 1"),
            readoutsFor(PlaybackState(isAudiobook = true, artist = "Bill Bryson", title = "Ch 1"), false),
        )
        assertEquals(
            Readouts("Podcast", "Show", "Episode", "Ep 2"),
            readoutsFor(PlaybackState(isPodcast = true, artist = "Show", title = "Ep 2"), false),
        )
    }

    @Test fun `tuning in overrides everything`() =
        assertEquals(Readouts("Station", "SleepRadio", "Now playing", "Tuning in…"), readoutsFor(music, true))

    @Test fun `status lamp text per source`() {
        assertEquals("TUNING IN", statusTextFor(music, true))
        assertEquals("ON AIR", statusTextFor(PlaybackState(isBroadcast = true), false))
        assertEquals("ON AIR — DJ", statusTextFor(PlaybackState(isBroadcast = true, djSpeaking = true), false))
        assertEquals("LIVE", statusTextFor(PlaybackState(isRadio = true), false))
        assertEquals("LIVE · BUFFERING", statusTextFor(PlaybackState(isRadio = true, isBuffering = true), false))
        assertNull(statusTextFor(music, false))                                    // music/books/podcasts: lamp dark
        assertNull(statusTextFor(PlaybackState(isAudiobook = true), false))
    }
}
