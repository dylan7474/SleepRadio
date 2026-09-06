package org.dylanjones.sleepradio.core.data

import kotlinx.coroutines.flow.Flow
import org.dylanjones.sleepradio.core.audio.AmbientPattern
import org.dylanjones.sleepradio.core.design.SkinId

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
}
