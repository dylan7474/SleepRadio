package org.dylanjones.sleepradio.feature.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dylanjones.sleepradio.core.broadcast.Chattiness
import org.dylanjones.sleepradio.core.data.BROADCAST_VOICE_OFF
import org.dylanjones.sleepradio.core.data.BROADCAST_VOICE_PERSONAL
import org.dylanjones.sleepradio.core.data.BROADCAST_VOICE_STOCK
import org.dylanjones.sleepradio.core.data.SettingsRepository
import org.dylanjones.sleepradio.core.tts.VoicePackInstaller
import org.dylanjones.sleepradio.core.tts.VoicePackResolver
import org.dylanjones.sleepradio.di.IoDispatcher
import java.io.IOException
import javax.inject.Inject

/**
 * Backs the "Broadcast voice" dialog: which voice the DJ uses, and installing /
 * importing / removing the stock and personal voice packs.
 *
 * Owns a plain [VoicePackInstaller] / [VoicePackResolver] (news them up rather
 * than injecting — a fresh @Inject type referenced here trips KSP2 on this
 * toolchain).
 */
@HiltViewModel
class BroadcastVoiceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val installer = VoicePackInstaller(context)
    private val resolver = VoicePackResolver(context)

    /** Bumped after an install/remove so the dialog re-reads what's on disk. */
    private val rescan = MutableStateFlow(0)

    data class UiState(
        val selected: String = BROADCAST_VOICE_OFF,
        val stockInstalled: Boolean = false,
        val personalInstalled: Boolean = false,
        val chattiness: Chattiness = Chattiness.DEFAULT,
        val announcerVolume: Float = 1f,
        val announcerSpeed: Float = 1f,
        val jinglesFolderSet: Boolean = false,
        val jingleEnabled: Boolean = false,
        val jingleEvery: Int = 4,
        val install: VoicePackInstaller.InstallState = VoicePackInstaller.InstallState.Idle,
    )

    val uiState: StateFlow<UiState> =
        combine(
            settings.broadcastVoice,
            settings.broadcastChattiness,
            combine(
                settings.broadcastAnnouncerVolume,
                settings.broadcastAnnouncerSpeed,
            ) { vol, speed -> vol to speed },
            combine(
                settings.broadcastJingleEnabled,
                settings.broadcastJingleEvery,
                settings.jinglesTreeUri,
            ) { enabled, every, tree -> Triple(enabled, every, tree != null) },
            combine(installer.state, rescan) { install, _ -> install },
        ) { selected, chat, (vol, speed), (jinEnabled, jinEvery, jinSet), install ->
            UiState(
                selected = selected,
                stockInstalled = resolver.isInstalled(VoicePackResolver.ID_STOCK),
                personalInstalled = resolver.isInstalled(VoicePackResolver.ID_PERSONAL),
                chattiness = Chattiness.fromId(chat),
                announcerVolume = vol,
                announcerSpeed = speed,
                jinglesFolderSet = jinSet,
                jingleEnabled = jinEnabled,
                jingleEvery = jinEvery,
                install = install,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun select(id: String) {
        viewModelScope.launch { settings.setBroadcastVoice(id) }
    }

    fun setChattiness(c: Chattiness) {
        viewModelScope.launch { settings.setBroadcastChattiness(c.id) }
    }

    fun setAnnouncerVolume(value: Float) {
        viewModelScope.launch { settings.setBroadcastAnnouncerVolume(value) }
    }

    fun setAnnouncerSpeed(value: Float) {
        viewModelScope.launch { settings.setBroadcastAnnouncerSpeed(value) }
    }

    fun setJingleEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setBroadcastJingleEnabled(enabled) }
    }

    fun setJingleEvery(tracks: Int) {
        viewModelScope.launch { settings.setBroadcastJingleEvery(tracks) }
    }

    fun downloadStock() {
        viewModelScope.launch {
            val ok = withContext(io) { installer.downloadStock() }
            if (ok) {
                rescan.update { it + 1 }
                settings.setBroadcastVoice(BROADCAST_VOICE_STOCK)
            }
        }
    }

    fun importVoice(uri: android.net.Uri, asPersonal: Boolean) {
        val id = if (asPersonal) BROADCAST_VOICE_PERSONAL else BROADCAST_VOICE_STOCK
        viewModelScope.launch {
            val ok = withContext(io) {
                installer.installZip(id) {
                    context.contentResolver.openInputStream(uri)
                        ?: throw IOException("Can't open $uri")
                }
            }
            if (ok) {
                rescan.update { it + 1 }
                settings.setBroadcastVoice(id)
            }
        }
    }

    fun removePersonal() {
        viewModelScope.launch {
            withContext(io) { installer.remove(VoicePackResolver.ID_PERSONAL) }
            if (uiState.value.selected == BROADCAST_VOICE_PERSONAL) {
                settings.setBroadcastVoice(BROADCAST_VOICE_OFF)
            }
            rescan.update { it + 1 }
        }
    }

    fun dismissInstallResult() = installer.resetState()
}
