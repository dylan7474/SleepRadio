package org.dylanjones.sleepradio.core.data

import kotlinx.coroutines.flow.Flow
import org.dylanjones.sleepradio.core.design.SkinId

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
}
