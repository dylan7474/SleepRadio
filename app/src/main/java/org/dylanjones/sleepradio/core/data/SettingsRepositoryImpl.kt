package org.dylanjones.sleepradio.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.dylanjones.sleepradio.core.audio.AmbientPattern
import org.dylanjones.sleepradio.core.audio.BinauralPreset
import org.dylanjones.sleepradio.core.audio.NoiseColor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

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

    override val broadcastNewsVoice: Flow<String> =
        dataStore.data.map { it[KEY_BROADCAST_NEWS_VOICE] ?: NEWS_VOICE_SAME }

    override suspend fun setBroadcastNewsVoice(id: String) {
        dataStore.edit { it[KEY_BROADCAST_NEWS_VOICE] = id }
    }

    override val broadcastChattiness: Flow<String> =
        dataStore.data.map { it[KEY_BROADCAST_CHATTINESS] ?: "balanced" }

    override suspend fun setBroadcastChattiness(id: String) {
        dataStore.edit { it[KEY_BROADCAST_CHATTINESS] = id }
    }

    override val broadcastAnnouncerVolume: Flow<Float> =
        dataStore.data.map { (it[KEY_BROADCAST_ANNOUNCER_VOLUME] ?: 1f).coerceIn(0f, 1f) }

    override suspend fun setBroadcastAnnouncerVolume(value: Float) {
        dataStore.edit { it[KEY_BROADCAST_ANNOUNCER_VOLUME] = value.coerceIn(0f, 1f) }
    }

    override val broadcastAnnouncerSpeed: Flow<Float> =
        dataStore.data.map { (it[KEY_BROADCAST_ANNOUNCER_SPEED] ?: 1f).coerceIn(0.5f, 2f) }

    override suspend fun setBroadcastAnnouncerSpeed(value: Float) {
        dataStore.edit { it[KEY_BROADCAST_ANNOUNCER_SPEED] = value.coerceIn(0.5f, 2f) }
    }

    override val broadcastNewsSpeed: Flow<Float> =
        dataStore.data.map { (it[KEY_BROADCAST_NEWS_SPEED] ?: DEFAULT_NEWS_SPEED).coerceIn(0.5f, 2f) }

    override suspend fun setBroadcastNewsSpeed(value: Float) {
        dataStore.edit { it[KEY_BROADCAST_NEWS_SPEED] = value.coerceIn(0.5f, 2f) }
    }

    override val jinglesTreeUri: Flow<String?> =
        dataStore.data.map { it[KEY_JINGLES_TREE] }

    override suspend fun setJinglesTreeUri(uri: String) {
        dataStore.edit { it[KEY_JINGLES_TREE] = uri }
    }

    override val broadcastJingleEnabled: Flow<Boolean> =
        dataStore.data.map { it[KEY_JINGLE_ENABLED] ?: false }

    override suspend fun setBroadcastJingleEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_JINGLE_ENABLED] = enabled }
    }

    override val broadcastJingleEvery: Flow<Int> =
        dataStore.data.map { (it[KEY_JINGLE_EVERY] ?: 4).coerceIn(1, 10) }

    override suspend fun setBroadcastJingleEvery(tracks: Int) {
        dataStore.edit { it[KEY_JINGLE_EVERY] = tracks.coerceIn(1, 10) }
    }

    override val broadcastDjHooks: Flow<Boolean> =
        dataStore.data.map { it[KEY_BROADCAST_DJ_HOOKS] ?: false }

    override suspend fun setBroadcastDjHooks(enabled: Boolean) {
        dataStore.edit { it[KEY_BROADCAST_DJ_HOOKS] = enabled }
    }

    override val broadcastNewsEnabled: Flow<Boolean> =
        dataStore.data.map { it[KEY_BROADCAST_NEWS_ENABLED] ?: false }

    override suspend fun setBroadcastNewsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_BROADCAST_NEWS_ENABLED] = enabled }
    }

    override val broadcastNewsQuietHours: Flow<Boolean> =
        dataStore.data.map { it[KEY_BROADCAST_NEWS_QUIET_HOURS] ?: true }

    override suspend fun setBroadcastNewsQuietHours(enabled: Boolean) {
        dataStore.edit { it[KEY_BROADCAST_NEWS_QUIET_HOURS] = enabled }
    }

    override val mixerLevels: Flow<MixerLevels?> = dataStore.data.map { prefs ->
        val volume = prefs[KEY_MIXER_VOLUME]
        val balance = prefs[KEY_MIXER_BALANCE]
        if (volume == null && balance == null) {
            null
        } else {
            MixerLevels(
                volume = (volume ?: 0.8f).takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.8f,
                balance = (balance ?: 0.5f).takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.5f,
            )
        }
    }

    override suspend fun setMixerLevels(levels: MixerLevels) {
        dataStore.edit {
            it[KEY_MIXER_VOLUME] = levels.volume.coerceIn(0f, 1f)
            it[KEY_MIXER_BALANCE] = levels.balance.coerceIn(0f, 1f)
        }
    }

    override val lampBrightness: Flow<Float> =
        dataStore.data.map { (it[KEY_LAMP_BRIGHTNESS] ?: 1f).takeIf { v -> v.isFinite() }?.coerceIn(0.2f, 1f) ?: 1f }

    override suspend fun setLampBrightness(value: Float) {
        dataStore.edit { it[KEY_LAMP_BRIGHTNESS] = value.coerceIn(0.2f, 1f) }
    }

    override val channelButtonMode: Flow<ChannelButtonMode> =
        dataStore.data.map { ChannelButtonMode.fromId(it[KEY_CHANNEL_BUTTON_MODE]) }

    override suspend fun setChannelButtonMode(mode: ChannelButtonMode) {
        dataStore.edit { it[KEY_CHANNEL_BUTTON_MODE] = mode.id }
    }

    override val screenRotation: Flow<ScreenRotation> =
        dataStore.data.map { ScreenRotation.fromId(it[KEY_SCREEN_ROTATION]) }

    override suspend fun setScreenRotation(mode: ScreenRotation) {
        dataStore.edit { it[KEY_SCREEN_ROTATION] = mode.id }
    }

    override val broadcastNewsQuietStartMin: Flow<Int> =
        dataStore.data.map { it[KEY_BROADCAST_NEWS_QUIET_START] ?: (23 * 60) }

    override val broadcastNewsQuietEndMin: Flow<Int> =
        dataStore.data.map { it[KEY_BROADCAST_NEWS_QUIET_END] ?: (6 * 60) }

    override suspend fun setBroadcastNewsQuietStartMin(minutes: Int) {
        dataStore.edit { it[KEY_BROADCAST_NEWS_QUIET_START] = minutes.coerceIn(0, 24 * 60 - 1) }
    }

    override suspend fun setBroadcastNewsQuietEndMin(minutes: Int) {
        dataStore.edit { it[KEY_BROADCAST_NEWS_QUIET_END] = minutes.coerceIn(0, 24 * 60 - 1) }
    }

    override val vuSyncAuto: Flow<Boolean> =
        dataStore.data.map { it[KEY_VU_SYNC_AUTO] ?: true }

    override suspend fun setVuSyncAuto(auto: Boolean) {
        dataStore.edit { it[KEY_VU_SYNC_AUTO] = auto }
    }

    override val vuDelayPhoneMs: Flow<Int> =
        dataStore.data.map { (it[KEY_VU_DELAY_PHONE] ?: 120).coerceIn(0, VU_DELAY_MAX_MS) }

    override suspend fun setVuDelayPhoneMs(ms: Int) {
        dataStore.edit { it[KEY_VU_DELAY_PHONE] = ms.coerceIn(0, VU_DELAY_MAX_MS) }
    }

    override val vuDelayBluetoothMs: Flow<Int> =
        dataStore.data.map { (it[KEY_VU_DELAY_BT] ?: 260).coerceIn(0, VU_DELAY_MAX_MS) }

    override suspend fun setVuDelayBluetoothMs(ms: Int) {
        dataStore.edit { it[KEY_VU_DELAY_BT] = ms.coerceIn(0, VU_DELAY_MAX_MS) }
    }

    override val vuDelayCustomMs: Flow<Int> =
        dataStore.data.map { (it[KEY_VU_DELAY_CUSTOM] ?: 150).coerceIn(0, VU_DELAY_MAX_MS) }

    override suspend fun setVuDelayCustomMs(ms: Int) {
        dataStore.edit { it[KEY_VU_DELAY_CUSTOM] = ms.coerceIn(0, VU_DELAY_MAX_MS) }
    }

    override suspend fun exportAll(): Map<String, Any> {
        val prefs = dataStore.data.first()
        return prefs.asMap().entries.associate { (key, value) -> key.name to value }
    }

    override suspend fun importAll(values: Map<String, Any>) {
        dataStore.edit { prefs ->
            prefs.clear()
            for ((name, value) in values) {
                // A whole-number Float ("1.0") is written to the backup JSON as "1" and comes back
                // as an Int/Long; stored under an int key it would throw ClassCastException the
                // moment the real Float key is read (crash-loop on every launch). So keys we know
                // are Floats are always restored as Floats, whatever number type arrived.
                if (name in FLOAT_KEY_NAMES && (value is Number)) {
                    prefs[floatPreferencesKey(name)] = value.toFloat()
                    continue
                }
                when (value) {
                    is String -> prefs[stringPreferencesKey(name)] = value
                    is Boolean -> prefs[booleanPreferencesKey(name)] = value
                    is Int -> prefs[intPreferencesKey(name)] = value
                    is Long -> prefs[longPreferencesKey(name)] = value
                    is Float -> prefs[floatPreferencesKey(name)] = value
                    // JSON has no Float type, so every decimal value that
                    // round-tripped through a backup zip decodes as Double
                    // (org.json always parses a non-integer number literal as
                    // Double) -- this app has zero real doublePreferencesKey
                    // settings, so any Double here is really a Float that
                    // went through JSON. Writing it back under a
                    // doublePreferencesKey (a genuinely different typed key,
                    // same name) corrupted the entry: the real Float read
                    // elsewhere later throws ClassCastException at the
                    // DataStore layer -- crash-looped every launch once a
                    // restore had run. Coerce back to Float instead.
                    is Double -> prefs[floatPreferencesKey(name)] = value.toFloat()
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        prefs[stringSetPreferencesKey(name)] = value as Set<String>
                    }
                }
            }
        }
    }

    private companion object {
        val KEY_AUDIOBOOKS_TREE = stringPreferencesKey("audiobooks_tree_uri")
        val KEY_MUSIC_TREE = stringPreferencesKey("music_tree_uri")
        val KEY_AMBIENT = stringPreferencesKey("ambient_current")
        val KEY_SLEEP_MIN = intPreferencesKey("sleep_duration_min")
        val KEY_CUSTOM_STATIONS = stringPreferencesKey("custom_stations")
        val KEY_BROADCAST_VOICE = stringPreferencesKey("broadcast_voice")
        val KEY_BROADCAST_NEWS_VOICE = stringPreferencesKey("broadcast_news_voice")
        val KEY_BROADCAST_CHATTINESS = stringPreferencesKey("broadcast_chattiness")
        val KEY_BROADCAST_ANNOUNCER_VOLUME = floatPreferencesKey("broadcast_announcer_volume")
        val KEY_BROADCAST_ANNOUNCER_SPEED = floatPreferencesKey("broadcast_announcer_speed")
        val KEY_BROADCAST_NEWS_SPEED = floatPreferencesKey("broadcast_news_speed")
        const val DEFAULT_NEWS_SPEED = 0.75f
        val KEY_JINGLES_TREE = stringPreferencesKey("jingles_tree_uri")
        val KEY_JINGLE_ENABLED = booleanPreferencesKey("broadcast_jingle_enabled")
        val KEY_JINGLE_EVERY = intPreferencesKey("broadcast_jingle_every")
        val KEY_BROADCAST_DJ_HOOKS = booleanPreferencesKey("broadcast_dj_hooks")
        val KEY_BROADCAST_NEWS_ENABLED = booleanPreferencesKey("broadcast_news_enabled")
        val KEY_BROADCAST_NEWS_QUIET_HOURS = booleanPreferencesKey("broadcast_news_quiet_hours")
        val KEY_LAMP_BRIGHTNESS = floatPreferencesKey("lamp_brightness")
        val KEY_CHANNEL_BUTTON_MODE = stringPreferencesKey("channel_button_mode")
        val KEY_SCREEN_ROTATION = stringPreferencesKey("screen_rotation")
        val KEY_MIXER_VOLUME = floatPreferencesKey("mixer_volume")
        val KEY_MIXER_BALANCE = floatPreferencesKey("mixer_balance")

        /** Every Float-typed key. Keep in step with the floatPreferencesKey(...) definitions above:
         *  importAll() uses it to restore whole-number floats (see there). */
        val FLOAT_KEY_NAMES: Set<String> = setOf(
            KEY_BROADCAST_ANNOUNCER_VOLUME.name,
            KEY_BROADCAST_ANNOUNCER_SPEED.name,
            KEY_BROADCAST_NEWS_SPEED.name,
            KEY_MIXER_VOLUME.name,
            KEY_MIXER_BALANCE.name,
            KEY_LAMP_BRIGHTNESS.name,
        )
        val KEY_BROADCAST_NEWS_QUIET_START = intPreferencesKey("broadcast_news_quiet_start_min")
        val KEY_BROADCAST_NEWS_QUIET_END = intPreferencesKey("broadcast_news_quiet_end_min")
        val KEY_VU_SYNC_AUTO = booleanPreferencesKey("vu_sync_auto")
        val KEY_VU_DELAY_PHONE = intPreferencesKey("vu_delay_phone_ms")
        val KEY_VU_DELAY_BT = intPreferencesKey("vu_delay_bt_ms")
        val KEY_VU_DELAY_CUSTOM = intPreferencesKey("vu_delay_custom_ms")

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
