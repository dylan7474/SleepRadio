package org.dylanjones.sleepradio.core.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupCodecTest {

    @Test
    fun `settings map round-trips through JSON, every DataStore primitive type`() {
        val values: Map<String, Any> = mapOf(
            "skin_id" to "STUDIO",
            "sleep_duration_min" to 45,
            "broadcast_announcer_volume" to 0.8f.toDouble(),
            "vu_delay_phone_ms" to 120,
            "broadcast_jingle_enabled" to true,
            "some_long" to 123456789012345L,
            "a_set" to setOf("one", "two", "three"),
        )
        val decoded = decodeSettings(encodeSettings(values))

        assertEquals(values["skin_id"], decoded["skin_id"])
        assertEquals(values["sleep_duration_min"], decoded["sleep_duration_min"])
        assertEquals(values["broadcast_jingle_enabled"], decoded["broadcast_jingle_enabled"])
        assertEquals(values["some_long"], decoded["some_long"])
        assertEquals(values["a_set"], decoded["a_set"])
    }

    @Test
    fun `empty settings map round-trips to empty`() {
        assertEquals(emptyMap<String, Any>(), decodeSettings(encodeSettings(emptyMap())))
    }
}
