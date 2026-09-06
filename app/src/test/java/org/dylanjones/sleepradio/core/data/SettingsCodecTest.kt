package org.dylanjones.sleepradio.core.data

import org.dylanjones.sleepradio.core.audio.AmbientPattern
import org.dylanjones.sleepradio.core.audio.BinauralPreset
import org.dylanjones.sleepradio.core.audio.NoiseColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCodecTest {

    @Test
    fun `ambient pattern round-trips`() {
        val p = AmbientPattern(
            noiseEnabled = true,
            noiseColor = NoiseColor.DEEP_SPACE,
            binaural = BinauralPreset.RELAX,
            binauralLevel = 0.73f,
        )
        val decoded = decodePattern(encodePattern(p))!!
        assertEquals(p.noiseEnabled, decoded.noiseEnabled)
        assertEquals(p.noiseColor, decoded.noiseColor)
        assertEquals(p.binaural, decoded.binaural)
        assertEquals(p.binauralLevel, decoded.binauralLevel, 1e-4f)
    }

    @Test
    fun `decodePattern is null-safe and tolerant of junk`() {
        assertNull(decodePattern(null))
        assertNull(decodePattern(""))
        assertNull(decodePattern("true|NOPE|RELAX|0.5"))
        assertNull(decodePattern("true|WHITE|RELAX"))
    }

    @Test
    fun `binaural level is clamped on decode`() {
        val decoded = decodePattern("false|WHITE|OFF|9.9")!!
        assertEquals(1f, decoded.binauralLevel, 1e-4f)
    }

    @Test
    fun `custom stations round-trip, order preserved`() {
        val list = listOf(
            RadioStation("custom_a", "My Jazz", "http://example.com/jazz", "Added by you"),
            RadioStation("rb_123", "SomaFM Drone", "https://ice.example/drone", "MP3 · 128k · US"),
        )
        val decoded = decodeStations(encodeStations(list))
        assertEquals(list, decoded)
    }

    @Test
    fun `field separators inside values are sanitised, not corrupting the record`() {
        val nasty = RadioStation("id1", "Name\nwith break", "http://x/y", "d")
        val decoded = decodeStations(encodeStations(listOf(nasty)))
        assertEquals(1, decoded.size)
        assertEquals("id1", decoded[0].id)
        assertEquals("http://x/y", decoded[0].streamUrl)
        assertTrue('\n' !in decoded[0].name)
    }

    @Test
    fun `decodeStations skips malformed lines`() {
        assertEquals(emptyList<RadioStation>(), decodeStations(null))
        assertEquals(emptyList<RadioStation>(), decodeStations("just-one-field"))
    }
}
