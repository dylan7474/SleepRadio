package org.dylanjones.sleepradio.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.dylanjones.sleepradio.core.audio.AmbientPattern
import org.dylanjones.sleepradio.core.audio.BinauralPreset
import org.dylanjones.sleepradio.core.audio.NoiseColor
import org.dylanjones.sleepradio.core.design.SkinId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val skin: Flow<SkinId> = dataStore.data.map { prefs ->
        when (prefs[KEY_SKIN]) {
            SkinId.INDUSTRIAL.name -> SkinId.INDUSTRIAL
            else -> SkinId.NEON
        }
    }

    override val skinChosen: Flow<Boolean> = dataStore.data.map { it[KEY_SKIN] != null }

    override suspend fun setSkin(skin: SkinId) {
        dataStore.edit { it[KEY_SKIN] = skin.name }
    }

    override val audiobooksTreeUri: Flow<String?> =
        dataStore.data.map { it[KEY_AUDIOBOOKS_TREE] }

    override suspend fun setAudiobooksTreeUri(uri: String) {
        dataStore.edit { it[KEY_AUDIOBOOKS_TREE] = uri }
    }

    override val musicTreeUri: Flow<String?> =
        dataStore.data.map { it[KEY_MUSIC_TREE] }

    override suspend fun setMusicTreeUri(uri: String) {
        dataStore.edit { it[KEY_MUSIC_TREE] = uri }
    }

    override val ambient: Flow<AmbientPattern?> =
        dataStore.data.map { decodePattern(it[KEY_AMBIENT]) }

    override suspend fun setAmbient(pattern: AmbientPattern) {
        dataStore.edit { it[KEY_AMBIENT] = encodePattern(pattern) }
    }

    override val ambientPatterns: Flow<List<AmbientPattern?>> =
        dataStore.data.map { prefs ->
            List(AMBIENT_PATTERN_SLOTS) { i -> decodePattern(prefs[patternKey(i)]) }
        }

    override suspend fun setAmbientPattern(index: Int, pattern: AmbientPattern?) {
        dataStore.edit { prefs ->
            val key = patternKey(index)
            if (pattern == null) prefs.remove(key) else prefs[key] = encodePattern(pattern)
        }
    }

    override val sleepDurationMin: Flow<Int> =
        dataStore.data.map { (it[KEY_SLEEP_MIN] ?: 30).coerceIn(1, 600) }

    override suspend fun setSleepDurationMin(minutes: Int) {
        dataStore.edit { it[KEY_SLEEP_MIN] = minutes.coerceIn(1, 600) }
    }

    override val customStations: Flow<List<RadioStation>> =
        dataStore.data.map { prefs -> decodeStations(prefs[KEY_CUSTOM_STATIONS]) }

    override suspend fun addCustomStation(station: RadioStation) {
        dataStore.edit { prefs ->
            val list = decodeStations(prefs[KEY_CUSTOM_STATIONS])
                .filterNot { it.id == station.id || it.streamUrl == station.streamUrl } + station
            prefs[KEY_CUSTOM_STATIONS] = encodeStations(list)
        }
    }

    override suspend fun removeCustomStation(id: String) {
        dataStore.edit { prefs ->
            val list = decodeStations(prefs[KEY_CUSTOM_STATIONS]).filterNot { it.id == id }
            prefs[KEY_CUSTOM_STATIONS] = encodeStations(list)
        }
    }

    override val broadcastVoice: Flow<String> =
        dataStore.data.map { it[KEY_BROADCAST_VOICE] ?: BROADCAST_VOICE_OFF }

    override suspend fun setBroadcastVoice(id: String) {
        dataStore.edit { it[KEY_BROADCAST_VOICE] = id }
    }

    override val broadcastChattiness: Flow<String> =
        dataStore.data.map { it[KEY_BROADCAST_CHATTINESS] ?: "balanced" }

    override suspend fun setBroadcastChattiness(id: String) {
        dataStore.edit { it[KEY_BROADCAST_CHATTINESS] = id }
    }

    private companion object {
        val KEY_SKIN = stringPreferencesKey("skin_id")
        val KEY_AUDIOBOOKS_TREE = stringPreferencesKey("audiobooks_tree_uri")
        val KEY_MUSIC_TREE = stringPreferencesKey("music_tree_uri")
        val KEY_AMBIENT = stringPreferencesKey("ambient_current")
        val KEY_SLEEP_MIN = intPreferencesKey("sleep_duration_min")
        val KEY_CUSTOM_STATIONS = stringPreferencesKey("custom_stations")
        val KEY_BROADCAST_VOICE = stringPreferencesKey("broadcast_voice")
        val KEY_BROADCAST_CHATTINESS = stringPreferencesKey("broadcast_chattiness")

        fun patternKey(index: Int) = stringPreferencesKey("ambient_pattern_$index")
    }
}

// --- DataStore string codecs (file-level so they're unit-testable) ---

/** Records separated by '\n', fields within a record by the unit-separator char. */
private const val FS = '\u001F'

internal fun encodeStations(list: List<RadioStation>): String = list.joinToString("\n") {
    listOf(it.id, it.name, it.streamUrl, it.description)
        .joinToString(FS.toString()) { f -> f.replace('\n', ' ').replace(FS, ' ') }
}

internal fun decodeStations(raw: String?): List<RadioStation> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split('\n').mapNotNull { line ->
        val p = line.split(FS)
        if (p.size < 3 || p[0].isBlank() || p[2].isBlank()) return@mapNotNull null
        RadioStation(
            id = p[0],
            name = p[1],
            streamUrl = p[2],
            description = p.getOrElse(3) { "" },
        )
    }
}

/** "noiseEnabled|noiseColor|binaural|binauralLevel" */
internal fun encodePattern(p: AmbientPattern): String =
    "${p.noiseEnabled}|${p.noiseColor.name}|${p.binaural.name}|${p.binauralLevel}"

internal fun decodePattern(raw: String?): AmbientPattern? {
    val parts = raw?.split('|') ?: return null
    if (parts.size != 4) return null
    return try {
        AmbientPattern(
            noiseEnabled = parts[0].toBooleanStrict(),
            noiseColor = NoiseColor.valueOf(parts[1]),
            binaural = BinauralPreset.valueOf(parts[2]),
            binauralLevel = parts[3].toFloat().coerceIn(0f, 1f),
        )
    } catch (_: IllegalArgumentException) {
        null
    }
}
