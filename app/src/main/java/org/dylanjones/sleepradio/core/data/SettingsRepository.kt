package org.dylanjones.sleepradio.core.data

import kotlinx.coroutines.flow.Flow
import org.dylanjones.sleepradio.core.audio.AmbientPattern
import org.dylanjones.sleepradio.core.design.SkinId

// RadioStation lives in this package (SourceModels.kt).

/** Number of saveable ambient PATTERN slots. */
const val AMBIENT_PATTERN_SLOTS = 3

/**
 * App-wide user preferences, backed by DataStore.
 */
interface SettingsRepository {
    /** Currently selected visual skin. Emits [SkinId.NEON] before the user has chosen. */
    val skin: Flow<SkinId>

    /** True once the user has explicitly picked a skin (drives the first-run picker). */
    val skinChosen: Flow<Boolean>

    suspend fun setSkin(skin: SkinId)

    /** Persisted SAF tree URI for the audiobooks root folder, or null. */
    val audiobooksTreeUri: Flow<String?>

    suspend fun setAudiobooksTreeUri(uri: String)

    /** Persisted SAF tree URI for a music root folder, or null. */
    val musicTreeUri: Flow<String?>

    suspend fun setMusicTreeUri(uri: String)

    /** Last-used ambient mix, restored on launch. Null before anything is saved. */
    val ambient: Flow<AmbientPattern?>

    suspend fun setAmbient(pattern: AmbientPattern)

    /** The [AMBIENT_PATTERN_SLOTS] saved PATTERN slots (null = empty). */
    val ambientPatterns: Flow<List<AmbientPattern?>>

    suspend fun setAmbientPattern(index: Int, pattern: AmbientPattern?)

    /** Sleep-timer duration in minutes (default 30). The running timer is not persisted. */
    val sleepDurationMin: Flow<Int>

    suspend fun setSleepDurationMin(minutes: Int)

    /** User-added radio stations (manual entry or from the online directory),
     *  in the order they were added. */
    val customStations: Flow<List<RadioStation>>

    suspend fun addCustomStation(station: RadioStation)

    suspend fun removeCustomStation(id: String)

    /**
     * Which voice the Broadcast DJ uses: [BROADCAST_VOICE_OFF] (music-only, no
     * spoken links — the default), [BROADCAST_VOICE_STOCK] or
     * [BROADCAST_VOICE_PERSONAL]. Persisted; the pack must still be installed
     * under `filesDir/tts/<id>/` for a non-off value to actually speak.
     */
    val broadcastVoice: Flow<String>

    suspend fun setBroadcastVoice(id: String)

    /** How chatty the auto-DJ is: a [org.dylanjones.sleepradio.core.broadcast.Chattiness] id. */
    val broadcastChattiness: Flow<String>

    suspend fun setBroadcastChattiness(id: String)

    /** DJ voice level, 0..1, applied on top of the master VOL. Default 1. */
    val broadcastAnnouncerVolume: Flow<Float>

    suspend fun setBroadcastAnnouncerVolume(value: Float)

    /** DJ speech rate: 1.0 = the voice's natural rate, higher is faster. Default 1. */
    val broadcastAnnouncerSpeed: Flow<Float>

    suspend fun setBroadcastAnnouncerSpeed(value: Float)
}

const val BROADCAST_VOICE_OFF = "off"
const val BROADCAST_VOICE_STOCK = "stock"
const val BROADCAST_VOICE_PERSONAL = "personal"
