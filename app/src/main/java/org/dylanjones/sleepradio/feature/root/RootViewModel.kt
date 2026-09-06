package org.dylanjones.sleepradio.feature.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.dylanjones.sleepradio.core.data.SettingsRepository
import org.dylanjones.sleepradio.core.design.SkinId
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    val skinId: StateFlow<SkinId> =
        settings.skin.stateIn(viewModelScope, SharingStarted.Eagerly, SkinId.NEON)

    val skinChosen: StateFlow<Boolean> =
        settings.skinChosen.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun chooseSkin(id: SkinId) {
        viewModelScope.launch { settings.setSkin(id) }
    }
}
