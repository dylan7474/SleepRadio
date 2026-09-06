package org.dylanjones.sleepradio.feature.player

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.dylanjones.sleepradio.core.audio.AmbientPattern
import org.dylanjones.sleepradio.core.audio.BinauralPreset
import org.dylanjones.sleepradio.core.audio.NoiseColor
import org.dylanjones.sleepradio.core.data.Audiobook
import org.dylanjones.sleepradio.core.data.FolderAlbum
import org.dylanjones.sleepradio.core.data.RadioStation
import org.dylanjones.sleepradio.core.design.SkinBackground
import org.dylanjones.sleepradio.core.design.SkinId
import org.dylanjones.sleepradio.core.design.skinFor
import org.dylanjones.sleepradio.feature.root.RootViewModel

@Composable
fun PlayerRoute(
    rootViewModel: RootViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val skinId by rootViewModel.skinId.collectAsStateWithLifecycle()
    val skinChosen by rootViewModel.skinChosen.collectAsStateWithLifecycle()
    val state by playerViewModel.uiState.collectAsStateWithLifecycle()
    val skin = skinFor(skinId)

    val context = LocalContext.current

    fun persistTreeGrant(uri: android.net.Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    val audiobooksFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            persistTreeGrant(uri)
            playerViewModel.onAudiobooksFolderChosen(uri.toString())
        }
    }

    val musicFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            persistTreeGrant(uri)
            playerViewModel.onMusicFolderChosen(uri.toString())
        }
    }

    var menuOpen by remember { mutableStateOf(false) }
    var ambientDialogOpen by remember { mutableStateOf(false) }
    var sleepDialogOpen by remember { mutableStateOf(false) }

    val actions = PlayerActions(
        onMenu = { menuOpen = true },
        onBell = {},
        onPresetClick = playerViewModel::onPresetClicked,
        onPresetLongClick = playerViewModel::clearSlot,
        onPlayPause = playerViewModel::playPause,
        onNext = playerViewModel::next,
        onPrevious = playerViewModel::previous,
        onSeek = playerViewModel::seekTo,
        onVolumeChange = playerViewModel::onVolumeChange,
        onBalanceChange = playerViewModel::onBalanceChange,
        onSleepTap = playerViewModel::onSleepTap,
        onSleepDurationPick = { sleepDialogOpen = true },
        onNoiseToggle = playerViewModel::toggleNoise,
        onNoiseColorPick = { ambientDialogOpen = true },
    )

    SkinBackground(skin) {
        PlayerScreen(state, actions, Modifier.fillMaxSize())

        // Skin switch menu, anchored near the ≡ button.
        Box(Modifier.safeDrawingPadding().padding(start = 20.dp, top = 8.dp)) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Neon skin") },
                    onClick = { rootViewModel.chooseSkin(SkinId.NEON); menuOpen = false },
                )
                DropdownMenuItem(
                    text = { Text("Industrial skin") },
                    onClick = { rootViewModel.chooseSkin(SkinId.INDUSTRIAL); menuOpen = false },
                )
            }
        }

        if (!skinChosen) {
            SkinPickerOverlay(onPick = rootViewModel::chooseSkin)
        }

        if (sleepDialogOpen) {
            SleepDurationDialog(
                current = state.sleepDurationMin,
                onPick = {
                    playerViewModel.setSleepDuration(it)
                    sleepDialogOpen = false
                },
                onDismiss = { sleepDialogOpen = false },
            )
        }

        if (ambientDialogOpen) {
            AmbientDialog(
                noiseColor = state.noiseColor,
                binaural = state.binaural,
                binauralLevel = state.binauralLevel,
                patterns = state.patterns,
                onPickNoise = playerViewModel::setNoiseColor,
                onPickBinaural = playerViewModel::setBinaural,
                onBinauralLevel = playerViewModel::setBinauralLevel,
                onRecallPattern = playerViewModel::recallPattern,
                onSavePattern = playerViewModel::savePattern,
                onClearPattern = playerViewModel::clearPattern,
                onDismiss = { ambientDialogOpen = false },
            )
        }

        state.pickerForSlot?.let { slotIndex ->
            SourcePickerDialog(
                stations = state.stations,
                folderAlbums = state.folderAlbums,
                musicFolderChosen = state.musicFolderChosen,
                audiobooks = state.audiobooks,
                audiobooksFolderChosen = state.audiobooksFolderChosen,
                onPickStation = { playerViewModel.assignStationToSlot(slotIndex, it) },
                onPickFolderAlbum = { playerViewModel.assignFolderAlbumToSlot(slotIndex, it) },
                onPickAudiobook = { playerViewModel.assignAudiobookToSlot(slotIndex, it) },
                onChooseAudiobooksFolder = { audiobooksFolderLauncher.launch(null) },
                onChooseMusicFolder = { musicFolderLauncher.launch(null) },
                onDismiss = playerViewModel::dismissPicker,
            )
        }
    }
}

@Composable
private fun SkinPickerOverlay(onPick: (SkinId) -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xCC000000))
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Choose a look",
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Switch anytime from the ≡ menu",
                color = androidx.compose.ui.graphics.Color(0xFFB0B0B0),
                fontSize = 13.sp,
            )
            Spacer(Modifier.padding(8.dp))
            SkinChoiceCard("NEON", "Cyberpunk cyan & magenta") { onPick(SkinId.NEON) }
            Spacer(Modifier.padding(6.dp))
            SkinChoiceCard("INDUSTRIAL", "Brushed steel, blue & amber") { onPick(SkinId.INDUSTRIAL) }
        }
    }
}

@Composable
private fun SkinChoiceCard(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(androidx.compose.ui.graphics.Color(0xFF1B1E27))
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Text(title, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(subtitle, color = androidx.compose.ui.graphics.Color(0xFF9AA4B2), fontSize = 12.sp)
    }
}

@Composable
private fun SourcePickerDialog(
    stations: List<RadioStation>,
    folderAlbums: List<FolderAlbum>,
    musicFolderChosen: Boolean,
    audiobooks: List<Audiobook>,
    audiobooksFolderChosen: Boolean,
    onPickStation: (RadioStation) -> Unit,
    onPickFolderAlbum: (FolderAlbum) -> Unit,
    onPickAudiobook: (Audiobook) -> Unit,
    onChooseAudiobooksFolder: () -> Unit,
    onChooseMusicFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Assign a source") },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                item { SectionHeader("INTERNET RADIO") }
                items(stations, key = { it.id }) { station ->
                    PickerRow(station.name, station.description) { onPickStation(station) }
                    HorizontalDivider()
                }

                item { SectionHeader("AUDIOBOOKS") }
                item {
                    PickerRow(
                        primary = if (audiobooksFolderChosen) "Change audiobooks folder…" else "Choose audiobooks folder…",
                        secondary = "Pick a folder of book folders",
                        onClick = onChooseAudiobooksFolder,
                    )
                    HorizontalDivider()
                }
                if (audiobooksFolderChosen && audiobooks.isEmpty()) {
                    item {
                        Text(
                            "No books found in that folder.",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
                items(audiobooks, key = { "book_${it.id}" }) { book ->
                    PickerRow(
                        primary = book.title,
                        secondary = "${book.chapterCount} chapter${if (book.chapterCount == 1) "" else "s"}",
                        onClick = { onPickAudiobook(book) },
                    )
                    HorizontalDivider()
                }

                item { SectionHeader("MUSIC") }
                item {
                    PickerRow(
                        primary = if (musicFolderChosen) "Change music folder…" else "Choose music folder…",
                        secondary = "Any subfolder with audio files is an album",
                        onClick = onChooseMusicFolder,
                    )
                    HorizontalDivider()
                }
                if (musicFolderChosen && folderAlbums.isEmpty()) {
                    item {
                        Text(
                            "No albums found in that folder.",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
                items(folderAlbums, key = { "folder_${it.id}" }) { album ->
                    PickerRow(
                        primary = album.title,
                        secondary = listOfNotNull(
                            album.artist.ifBlank { null },
                            "${album.trackCount} tracks",
                        ).joinToString(" · "),
                        onClick = { onPickFolderAlbum(album) },
                    )
                    HorizontalDivider()
                }
            }
        },
    )
}

@Composable
private fun SleepDurationDialog(
    current: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(5, 10, 15, 20, 30, 45, 60, 90)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Sleep timer") },
        text = {
            LazyColumn {
                items(options, key = { it }) { min ->
                    PickerRow(
                        primary = "$min minutes" + if (min == current) "  ● current" else "",
                        secondary = "Fade out & stop the main source; noise / binaural keep playing",
                        onClick = { onPick(min) },
                    )
                    HorizontalDivider()
                }
            }
        },
    )
}

@Composable
private fun AmbientDialog(
    noiseColor: NoiseColor,
    binaural: BinauralPreset,
    binauralLevel: Float,
    patterns: List<AmbientPattern?>,
    onPickNoise: (NoiseColor) -> Unit,
    onPickBinaural: (BinauralPreset) -> Unit,
    onBinauralLevel: (Float) -> Unit,
    onRecallPattern: (Int) -> Unit,
    onSavePattern: (Int) -> Unit,
    onClearPattern: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Ambient mix") },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                item { SectionHeader("PATTERNS — tap to recall, long-press to save") }
                itemsIndexed(patterns) { i, p ->
                    PatternRow(
                        index = i,
                        pattern = p,
                        onRecall = { onRecallPattern(i) },
                        onSave = { onSavePattern(i) },
                        onClear = { onClearPattern(i) },
                    )
                    HorizontalDivider()
                }

                item { SectionHeader("NOISE — SPECTRUM") }
                items(NoiseColor.entries.toList(), key = { "noise_${it.name}" }) { color ->
                    PickerRow(
                        primary = color.label() + if (color == noiseColor) "  ● current" else "",
                        secondary = color.blurb(),
                        onClick = { onPickNoise(color) },
                    )
                    HorizontalDivider()
                }

                item { SectionHeader("BINAURAL BEATS — USE HEADPHONES") }
                items(BinauralPreset.entries.toList(), key = { "bin_${it.name}" }) { preset ->
                    PickerRow(
                        primary = preset.label + if (preset == binaural) "  ● current" else "",
                        secondary = if (preset == BinauralPreset.OFF) {
                            "Channel C off"
                        } else {
                            "${preset.carrierHz.toInt()} Hz carrier · +${trimHz(preset.beatHz)} Hz beat"
                        },
                        onClick = { onPickBinaural(preset) },
                    )
                    HorizontalDivider()
                }
                item {
                    Text(
                        "Binaural level  ${(binauralLevel * 100).toInt()}%",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Slider(
                        value = binauralLevel,
                        onValueChange = onBinauralLevel,
                        valueRange = 0f..1f,
                    )
                }
            }
        },
    )
}

@Composable
private fun PatternRow(
    index: Int,
    pattern: AmbientPattern?,
    onRecall: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { if (pattern != null) onRecall() else onSave() }, onLongClick = onSave)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Pattern ${index + 1}", fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                pattern?.summary() ?: "Empty — long-press to save current mix",
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        if (pattern != null) {
            TextButton(onClick = onClear) { Text("Clear") }
        }
    }
}

private fun trimHz(v: Float): String =
    if (v % 1f == 0f) v.toInt().toString() else v.toString()

private fun NoiseColor.blurb(): String = when (this) {
    NoiseColor.WHITE -> "Flat spectrum — bright, full hiss"
    NoiseColor.PINK -> "−3 dB/oct — balanced, natural"
    NoiseColor.BROWN -> "−6 dB/oct — deep, rain-like rumble"
    NoiseColor.BLUE -> "+3 dB/oct — airy, high-frequency"
    NoiseColor.DEEP_SPACE -> "Very deep brown — distant hum"
    NoiseColor.AMBIENT -> "Pink with a slow swell"
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun PickerRow(primary: String, secondary: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(primary, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(secondary, fontSize = 12.sp, maxLines = 1, textAlign = TextAlign.Start)
    }
}
