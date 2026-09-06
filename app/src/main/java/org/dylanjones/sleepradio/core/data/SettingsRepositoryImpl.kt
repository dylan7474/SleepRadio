package org.dylanjones.sleepradio.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

    private companion object {
        val KEY_SKIN = stringPreferencesKey("skin_id")
        val KEY_AUDIOBOOKS_TREE = stringPreferencesKey("audiobooks_tree_uri")
    }
}
