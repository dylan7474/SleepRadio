package org.dylanjones.sleepradio.feature.player

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
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

    // Keep the screen awake while the player is on screen, so the phone doesn't
    // dim mid-setup. Once a sleep timer is running, let it dim/sleep normally.
    val view = LocalView.current
    val keepAwake = !state.sleepActive
    DisposableEffect(view, keepAwake) {
        view.keepScreenOn = keepAwake
        onDispose { view.keepScreenOn = false }
    }

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

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    fun closeDrawer() = scope.launch { drawerState.close() }

    var ambientDialogOpen by remember { mutableStateOf(false) }
    var sleepDialogOpen by remember { mutableStateOf(false) }
    var addStationOpen by remember { mutableStateOf(false) }
    var directoryOpen by remember { mutableStateOf(false) }
    /** Slot to assign a directory pick to, or null = "play now" from the drawer. */
    var directoryAssignSlot by remember { mutableStateOf<Int?>(null) }
    var radioStationsOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }

    val actions = PlayerActions(
        onMenu = { scope.launch { drawerState.open() } },
        onBell = { aboutOpen = true },
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                currentSkin = skinId,
                onNowPlaying = { closeDrawer() },
                onAmbient = { closeDrawer(); ambientDialogOpen = true },
                onSleep = { closeDrawer(); sleepDialogOpen = true },
                onRadioStations = { closeDrawer(); radioStationsOpen = true },
                onSkin = { rootViewModel.chooseSkin(it); closeDrawer() },
                onAbout = { closeDrawer(); aboutOpen = true },
            )
        },
    ) {
        SkinBackground(skin) {
            PlayerScreen(state, actions, Modifier.fillMaxSize())

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

            if (radioStationsOpen) {
                RadioStationsDialog(
                    stations = state.stations,
                    customStationIds = state.customStationIds,
                    onPlay = { playerViewModel.playStationNow(it); radioStationsOpen = false },
                    onRemove = playerViewModel::removeStation,
                    onAddManual = { addStationOpen = true },
                    onBrowseDirectory = { directoryAssignSlot = null; directoryOpen = true },
                    onDismiss = { radioStationsOpen = false },
                )
            }

            if (aboutOpen) {
                AboutDialog(onDismiss = { aboutOpen = false })
            }

            state.pickerForSlot?.let { slotIndex ->
                SourcePickerDialog(
                    stations = state.stations,
                    customStationIds = state.customStationIds,
                    folderAlbums = state.folderAlbums,
                    musicFolderChosen = state.musicFolderChosen,
                    audiobooks = state.audiobooks,
                    audiobooksFolderChosen = state.audiobooksFolderChosen,
                    onPickStation = { playerViewModel.assignStationToSlot(slotIndex, it) },
                    onRemoveStation = playerViewModel::removeStation,
                    onAddManual = { addStationOpen = true },
                    onBrowseDirectory = { directoryAssignSlot = slotIndex; directoryOpen = true },
                    onPickFolderAlbum = { playerViewModel.assignFolderAlbumToSlot(slotIndex, it) },
                    onPickAudiobook = { playerViewModel.assignAudiobookToSlot(slotIndex, it) },
                    onChooseAudiobooksFolder = { audiobooksFolderLauncher.launch(null) },
                    onChooseMusicFolder = { musicFolderLauncher.launch(null) },
                    onDismiss = playerViewModel::dismissPicker,
                )
            }

            if (addStationOpen) {
                AddStationDialog(
                    onAdd = { name, url -> playerViewModel.addManualStation(name, url) },
                    onDismiss = { addStationOpen = false },
                )
            }
            if (directoryOpen) {
                DirectoryDialog(
                    results = state.directoryResults,
                    searching = state.directorySearching,
                    onSearch = playerViewModel::searchDirectory,
                    onPick = { station ->
                        val slot = directoryAssignSlot
                        if (slot != null) {
                            playerViewModel.addAndAssignStation(slot, station)
                        } else {
                            playerViewModel.saveCustomStation(station)
                            playerViewModel.playStationNow(station)
                        }
                        directoryOpen = false
                        playerViewModel.clearDirectory()
                    },
                    onDismiss = {
                        directoryOpen = false
                        playerViewModel.clearDirectory()
                    },
                )
            }
        }
    }
}

@Composable
private fun AppDrawer(
    currentSkin: SkinId,
    onNowPlaying: () -> Unit,
    onAmbient: () -> Unit,
    onSleep: () -> Unit,
    onRadioStations: () -> Unit,
    onSkin: (SkinId) -> Unit,
    onAbout: () -> Unit,
) {
    ModalDrawerSheet {
        Column(
            Modifier
                .safeDrawingPadding()
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "SLEEPRADIO",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.padding(16.dp),
            )
            NavigationDrawerItem(
                label = { Text("Now playing") },
                selected = false,
                onClick = onNowPlaying,
            )
            NavigationDrawerItem(
                label = { Text("Ambient mix") },
                selected = false,
                onClick = onAmbient,
            )
            NavigationDrawerItem(
                label = { Text("Sleep timer") },
                selected = false,
                onClick = onSleep,
            )
            NavigationDrawerItem(
                label = { Text("Radio stations") },
                selected = false,
                onClick = onRadioStations,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader("APPEARANCE")
            NavigationDrawerItem(
                label = { Text("Neon") },
                selected = currentSkin == SkinId.NEON,
                onClick = { onSkin(SkinId.NEON) },
            )
            NavigationDrawerItem(
                label = { Text("Industrial") },
                selected = currentSkin == SkinId.INDUSTRIAL,
                onClick = { onSkin(SkinId.INDUSTRIAL) },
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            NavigationDrawerItem(
                label = { Text("About") },
                selected = false,
                onClick = onAbout,
            )
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text("SleepRadio") },
        text = {
            Column {
                Text("Version $version", fontSize = 13.sp)
                Spacer(Modifier.padding(6.dp))
                Text(
                    "A bedside player: a main source (local music, audiobooks or " +
                        "internet radio) plus coloured-noise and binaural-beat channels " +
                        "that keep going after the sleep timer stops the main audio.",
                    fontSize = 13.sp,
                )
                Spacer(Modifier.padding(6.dp))
                Text(
                    "Station directory data from radio-browser.info (community-run, " +
                        "public domain).",
                    fontSize = 12.sp,
                )
            }
        },
    )
}

@Composable
private fun RadioStationsDialog(
    stations: List<RadioStation>,
    customStationIds: Set<String>,
    onPlay: (RadioStation) -> Unit,
    onRemove: (String) -> Unit,
    onAddManual: () -> Unit,
    onBrowseDirectory: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Radio stations") },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                item {
                    PickerRow("＋  Add a station manually…", "Enter a name and stream URL", onAddManual)
                    HorizontalDivider()
                }
                item {
                    PickerRow("⌕  Browse the online directory…", "Search radio-browser.info", onBrowseDirectory)
                    HorizontalDivider()
                }
                item { SectionHeader("TAP TO PLAY NOW") }
                items(stations, key = { it.id }) { station ->
                    StationRow(
                        name = station.name,
                        description = station.description,
                        deletable = station.id in customStationIds,
                        onClick = { onPlay(station) },
                        onRemove = { onRemove(station.id) },
                    )
                    HorizontalDivider()
                }
            }
        },
    )
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
    customStationIds: Set<String>,
    folderAlbums: List<FolderAlbum>,
    musicFolderChosen: Boolean,
    audiobooks: List<Audiobook>,
    audiobooksFolderChosen: Boolean,
    onPickStation: (RadioStation) -> Unit,
    onRemoveStation: (String) -> Unit,
    onAddManual: () -> Unit,
    onBrowseDirectory: () -> Unit,
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
                item {
                    PickerRow("＋  Add a station manually…", "Enter a name and stream URL", onAddManual)
                    HorizontalDivider()
                }
                item {
                    PickerRow("⌕  Browse the online directory…", "Search radio-browser.info", onBrowseDirectory)
                    HorizontalDivider()
                }
                items(stations, key = { it.id }) { station ->
                    StationRow(
                        name = station.name,
                        description = station.description,
                        deletable = station.id in customStationIds,
                        onClick = { onPickStation(station) },
                        onRemove = { onRemoveStation(station.id) },
                    )
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

@Composable
private fun StationRow(
    name: String,
    description: String,
    deletable: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
        ) {
            Text(
                name,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(description, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (deletable) {
            TextButton(onClick = onRemove) { Text("Remove") }
        }
    }
}

@Composable
private fun AddStationDialog(
    onAdd: (name: String, url: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank(),
                onClick = { onAdd(name, url); onDismiss() },
            ) { Text("Add") }
        },
        title = { Text("Add a station") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.padding(6.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Stream URL") },
                    placeholder = { Text("http://…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (url.isNotBlank()) { onAdd(name, url); onDismiss() } },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
private fun DirectoryDialog(
    results: List<RadioStation>,
    searching: Boolean,
    onSearch: (String) -> Unit,
    onPick: (RadioStation) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Online directory") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search stations") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onSearch(query) }) { Text("Go") }
                }
                Spacer(Modifier.padding(4.dp))
                when {
                    searching -> Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) { CircularProgressIndicator() }

                    results.isEmpty() -> Text(
                        "Type a station name and tap Go.",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )

                    else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(results, key = { it.id }) { station ->
                            PickerRow(station.name, station.description) { onPick(station) }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
    )
}
