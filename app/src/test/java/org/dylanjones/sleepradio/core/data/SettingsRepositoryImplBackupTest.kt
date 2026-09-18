package org.dylanjones.sleepradio.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.dylanjones.sleepradio.core.backup.decodeSettings
import org.dylanjones.sleepradio.core.backup.encodeSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** The real backup path always writes/reads actual JSON *text* (a zip
 *  entry), which is what turns a Float into a Double -- org.json only
 *  coerces on parsing, not on a same-process JSONObject.put(). Skipping the
 *  text round trip here would silently not exercise the bug at all. */
private fun roundTripThroughJsonText(values: Map<String, Any>): Map<String, Any> =
    decodeSettings(JSONObject(encodeSettings(values).toString()))

/**
 * A setting round-tripped through a real backup zip goes:
 * DataStore -> exportAll() -> JSON ([encodeSettings]/[decodeSettings] in
 * [org.dylanjones.sleepradio.core.backup.BackupManager]) -> importAll() ->
 * DataStore. JSON has no Float type (org.json always parses a non-integer
 * number literal as Double), which is exactly the path that corrupted every
 * Float setting after a restore -- crash-looped the app on next launch
 * (`ClassCastException: Double cannot be cast to Float`, thrown deep in
 * DataStore's own `Preferences.get()`, since the restored value landed under
 * a `doublePreferencesKey` instead of the real `floatPreferencesKey` of the
 * same name). Exercising exportAll/importAll directly (skipping the JSON
 * step) would never have caught this -- the bug only exists once a Float
 * has round-tripped through JSON and come back as a Double.
 */
class SettingsRepositoryImplBackupTest {

    private fun newRepo(tmp: File) = SettingsRepositoryImpl(
        PreferenceDataStoreFactory.create(produceFile = { tmp }),
    )

    @Test
    fun `a Float setting survives a full export-to-JSON-to-import round trip`() = runTest {
        val file = File.createTempFile("settings_backup_test", ".preferences_pb")
        file.deleteOnExit()
        val repo = newRepo(file)

        repo.setBroadcastAnnouncerVolume(0.42f)
        repo.setBroadcastAnnouncerSpeed(1.15f)

        // The exact path a real backup takes: Map -> JSON text -> Map.
        val restored = roundTripThroughJsonText(repo.exportAll())
        repo.importAll(restored)

        assertEquals(0.42f, repo.broadcastAnnouncerVolume.first(), 1e-4f)
        assertEquals(1.15f, repo.broadcastAnnouncerSpeed.first(), 1e-4f)
    }

    @Test
    fun `importAll never writes a doublePreferencesKey -- this app has no real Double settings`() = runTest {
        val file = File.createTempFile("settings_backup_test2", ".preferences_pb")
        file.deleteOnExit()
        val repo = newRepo(file)

        repo.setBroadcastAnnouncerVolume(0.7f)
        val restored = roundTripThroughJsonText(repo.exportAll())
        repo.importAll(restored)

        // Reading it back through the real Float-typed flow must not throw
        // (a doublePreferencesKey/floatPreferencesKey type collision throws
        // inside DataStore's own Preferences.get(), not a Kotlin-level
        // exception this test could catch more directly).
        assertEquals(0.7f, repo.broadcastAnnouncerVolume.first(), 1e-4f)
    }
}
