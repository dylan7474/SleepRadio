package org.dylanjones.sleepradio.feature.player

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dylanjones.sleepradio.core.backup.BackupManager
import org.dylanjones.sleepradio.core.data.SettingsRepository
import org.dylanjones.sleepradio.core.data.db.AudiobookProgressDao
import org.dylanjones.sleepradio.core.data.db.PodcastFeedDao
import org.dylanjones.sleepradio.core.data.db.PodcastProgressDao
import org.dylanjones.sleepradio.core.data.db.SourceSlotDao
import org.dylanjones.sleepradio.di.IoDispatcher
import java.io.IOException
import javax.inject.Inject

/**
 * Backs the "Backup & restore" dialog. Owns a plain [BackupManager] (news it up
 * rather than injecting — a fresh @Inject type referenced here trips KSP2 on
 * this toolchain, same as [org.dylanjones.sleepradio.core.tts.VoicePackInstaller]).
 * Every other constructor param is an existing Hilt-provided type, so this
 * ViewModel itself is safe to @Inject.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    settings: SettingsRepository,
    sourceSlotDao: SourceSlotDao,
    audiobookProgressDao: AudiobookProgressDao,
    podcastFeedDao: PodcastFeedDao,
    podcastProgressDao: PodcastProgressDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val manager = BackupManager(
        context = context,
        settings = settings,
        sourceSlotDao = sourceSlotDao,
        audiobookProgressDao = audiobookProgressDao,
        podcastFeedDao = podcastFeedDao,
        podcastProgressDao = podcastProgressDao,
    )

    sealed interface Result {
        data object Idle : Result
        data object Working : Result
        data object ExportDone : Result
        data class RestoreDone(val voicesRestored: List<String>, val foldersToRepick: List<String>) : Result
        data class Failed(val reason: String) : Result
    }

    private val _result = MutableStateFlow<Result>(Result.Idle)
    val result: StateFlow<Result> = _result.asStateFlow()

    fun export(uri: Uri) {
        _result.value = Result.Working
        viewModelScope.launch {
            _result.value = try {
                withContext(io) {
                    val stream = context.contentResolver.openOutputStream(uri)
                        ?: throw IOException("Can't open $uri")
                    stream.use { manager.export(it) }
                }
                Result.ExportDone
            } catch (e: Exception) {
                Result.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun restore(uri: Uri) {
        _result.value = Result.Working
        viewModelScope.launch {
            _result.value = try {
                val restored = withContext(io) {
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: throw IOException("Can't open $uri")
                    stream.use { manager.import(it) }
                }
                Result.RestoreDone(restored.voicesRestored, restored.foldersToRepick)
            } catch (e: Exception) {
                Result.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun dismissResult() {
        _result.value = Result.Idle
    }
}
