package org.dylanjones.sleepradio.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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
import org.dylanjones.sleepradio.core.data.PodcastEpisode
import org.dylanjones.sleepradio.core.data.PodcastFeed
import org.dylanjones.sleepradio.core.data.PodcastProgress
import org.dylanjones.sleepradio.core.data.RadioStation
import org.dylanjones.sleepradio.core.broadcast.Chattiness
import org.dylanjones.sleepradio.core.data.BROADCAST_VOICE_OFF
import org.dylanjones.sleepradio.core.data.BROADCAST_VOICE_PERSONAL
import org.dylanjones.sleepradio.core.data.BROADCAST_VOICE_STOCK
import org.dylanjones.sleepradio.core.data.NEWS_VOICE_SAME
import org.dylanjones.sleepradio.core.design.RadioDialog
import org.dylanjones.sleepradio.core.design.RadioLamp
import org.dylanjones.sleepradio.core.design.RadioSlider
import org.dylanjones.sleepradio.core.design.RadioSwitch
import org.dylanjones.sleepradio.core.design.RadioTextButton
import org.dylanjones.sleepradio.core.design.SkinBackground
import org.dylanjones.sleepradio.core.design.ChannelKey
import org.dylanjones.sleepradio.core.data.ChannelButtonMode
import org.dylanjones.sleepradio.core.data.ScreenRotation
import org.dylanjones.sleepradio.core.design.LocalChannelButtonMode
import org.dylanjones.sleepradio.core.design.LocalLampBrightness
import org.dylanjones.sleepradio.core.design.RoundGlyph
import org.dylanjones.sleepradio.core.design.RoundKey
import org.dylanjones.sleepradio.core.design.StudioSkin
import org.dylanjones.sleepradio.core.tts.VoicePackInstaller
import org.dylanjones.sleepradio.core.tts.rememberDebugTtsTest
import kotlin.math.roundToInt

@Composable
fun PlayerRoute(
    playerViewModel: PlayerViewModel = hiltViewModel(),
    broadcastVoiceViewModel: BroadcastVoiceViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel(),
) {
    val state by playerViewModel.uiState.collectAsStateWithLifecycle()
    val skin = StudioSkin
    val lampBrightness by playerViewModel.lampBrightness.collectAsStateWithLifecycle()
    val channelMode by playerViewModel.channelButtonMode.collectAsStateWithLifecycle()
    val rotation by playerViewModel.screenRotation.collectAsStateWithLifecycle()

    val context = LocalContext.current

    // Apply the chosen screen rotation to the activity. "Auto" hands control back to the phone's own setting.
    LaunchedEffect(rotation, context) {
        var c: Context? = context
        while (c is ContextWrapper && c !is Activity) c = c.baseContext
        (c as? Activity)?.requestedOrientation = when (rotation) {
            ScreenRotation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ScreenRotation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ScreenRotation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

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

    val jinglesFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            persistTreeGrant(uri)
            playerViewModel.onJinglesFolderChosen(uri.toString())
        }
    }

    val importVoiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) broadcastVoiceViewModel.importVoice(uri, asPersonal = true)
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> if (uri != null) backupViewModel.export(uri) }

    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) backupViewModel.restore(uri) }

    // VU-sync mic calibration (Phase 16B) needs RECORD_AUDIO.
    var pendingCalBluetooth by remember { mutableStateOf<Boolean?>(null) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val bt = pendingCalBluetooth
        pendingCalBluetooth = null
        if (granted && bt != null) playerViewModel.startVuCalibration(bt)
    }
    fun beginVuCalibration(bluetooth: Boolean) {
        if (
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.RECORD_AUDIO,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            playerViewModel.startVuCalibration(bluetooth)
        } else {
            pendingCalBluetooth = bluetooth
            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    fun closeDrawer() = scope.launch { drawerState.close() }

    // Debug-only TTS smoke test (Phase 9 Chunk A); null in release builds.
    val onTtsTest = rememberDebugTtsTest()

    var ambientDialogOpen by remember { mutableStateOf(false) }
    var sleepDialogOpen by remember { mutableStateOf(false) }
    var addStationOpen by remember { mutableStateOf(false) }
    var directoryOpen by remember { mutableStateOf(false) }
    /** Slot to assign a directory pick to, or null = "play now" from the drawer. */
    var directoryAssignSlot by remember { mutableStateOf<Int?>(null) }
    var radioStationsOpen by remember { mutableStateOf(false) }
    var podcastsOpen by remember { mutableStateOf(false) }
    var podcastDirectoryOpen by remember { mutableStateOf(false) }
    var podcastAddUrlOpen by remember { mutableStateOf(false) }
    var broadcastVoiceOpen by remember { mutableStateOf(false) }
    var vuSyncOpen by remember { mutableStateOf(false) }
    var lampOpen by remember { mutableStateOf(false) }
    var channelModeOpen by remember { mutableStateOf(false) }
    var rotationOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var backupOpen by remember { mutableStateOf(false) }

    val actions = PlayerActions(
        onMenu = { scope.launch { drawerState.open() } },
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
                onNowPlaying = { closeDrawer() },
                onAmbient = { closeDrawer(); ambientDialogOpen = true },
                onSleep = { closeDrawer(); sleepDialogOpen = true },
                onRadioStations = { closeDrawer(); radioStationsOpen = true },
                onPodcasts = { closeDrawer(); podcastsOpen = true },
                onBroadcastVoice = { closeDrawer(); broadcastVoiceOpen = true },
                onVuSync = { closeDrawer(); vuSyncOpen = true },
                onLampBrightness = { closeDrawer(); lampOpen = true },
                onChannelButtons = { closeDrawer(); channelModeOpen = true },
                onScreenRotation = { closeDrawer(); rotationOpen = true },
                onAbout = { closeDrawer(); aboutOpen = true },
                onBackup = { closeDrawer(); backupOpen = true },
                onTtsTest = onTtsTest?.let { test -> { closeDrawer(); test() } },
            )
        },
    ) {
        SkinBackground(skin) {
          CompositionLocalProvider(
              LocalLampBrightness provides lampBrightness,
              LocalChannelButtonMode provides channelMode,
          ) {
            PlayerScreen(state, actions, Modifier.fillMaxSize(), vu = playerViewModel.vu)
          }

            if (rotationOpen) {
                ScreenRotationDialog(
                    current = rotation,
                    onPick = { playerViewModel.setScreenRotation(it) },
                    onDismiss = { rotationOpen = false },
                )
            }

            if (channelModeOpen) {
                ChannelButtonsDialog(
                    current = channelMode,
                    onPick = { playerViewModel.setChannelButtonMode(it) },
                    onDismiss = { channelModeOpen = false },
                )
            }

            if (lampOpen) {
                LampBrightnessDialog(
                    current = lampBrightness,
                    onCommit = playerViewModel::setLampBrightness,
                    onDismiss = { lampOpen = false },
                )
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

            if (vuSyncOpen) {
                val auto by playerViewModel.vuSyncAuto.collectAsStateWithLifecycle()
                val phoneMs by playerViewModel.vuDelayPhoneMs.collectAsStateWithLifecycle()
                val btMs by playerViewModel.vuDelayBluetoothMs.collectAsStateWithLifecycle()
                val customMs by playerViewModel.vuDelayCustomMs.collectAsStateWithLifecycle()
                val activeMs by playerViewModel.activeVuDelayMs.collectAsStateWithLifecycle()
                val calState by playerViewModel.vuCal.collectAsStateWithLifecycle()
                VuSyncDialog(
                    auto = auto,
                    phoneMs = phoneMs,
                    bluetoothMs = btMs,
                    customMs = customMs,
                    activeMs = activeMs,
                    calState = calState,
                    onAuto = playerViewModel::setVuSyncAuto,
                    onPhoneMs = playerViewModel::setVuDelayPhoneMs,
                    onBluetoothMs = playerViewModel::setVuDelayBluetoothMs,
                    onCustomMs = playerViewModel::setVuDelayCustomMs,
                    onCalibrate = ::beginVuCalibration,
                    onCalDismiss = playerViewModel::dismissVuCalibration,
                    onDismiss = { vuSyncOpen = false },
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

            if (podcastsOpen) {
                val openFeed = state.podcastOpenFeed
                if (openFeed != null) {
                    PodcastEpisodesDialog(
                        feed = openFeed,
                        episodes = state.podcastEpisodes,
                        loading = state.podcastEpisodesLoading,
                        error = state.podcastEpisodesError,
                        progress = state.podcastProgress,
                        onPlay = { playerViewModel.playPodcastEpisodeNow(openFeed, it); podcastsOpen = false },
                        onBack = playerViewModel::closePodcastFeed,
                        onDismiss = { playerViewModel.closePodcastFeed(); podcastsOpen = false },
                    )
                } else {
                    PodcastsDialog(
                        feeds = state.podcastFeeds,
                        onOpenFeed = playerViewModel::openPodcastFeed,
                        onUnsubscribe = playerViewModel::unsubscribePodcast,
                        onAddByUrl = { podcastAddUrlOpen = true },
                        onBrowseDirectory = { podcastDirectoryOpen = true },
                        onDismiss = { podcastsOpen = false },
                    )
                }
            }

            if (podcastAddUrlOpen) {
                AddPodcastDialog(
                    onAdd = { url -> playerViewModel.addPodcastByUrl(url) },
                    onDismiss = { podcastAddUrlOpen = false },
                )
            }

            if (podcastDirectoryOpen) {
                PodcastDirectoryDialog(
                    results = state.podcastSearchResults,
                    searching = state.podcastSearching,
                    onSearch = playerViewModel::searchPodcasts,
                    onPick = { feed ->
                        playerViewModel.subscribePodcast(feed)
                        playerViewModel.openPodcastFeed(feed)
                        podcastDirectoryOpen = false
                        playerViewModel.clearPodcastSearch()
                    },
                    onDismiss = {
                        podcastDirectoryOpen = false
                        playerViewModel.clearPodcastSearch()
                    },
                )
            }

            if (broadcastVoiceOpen) {
                val voiceState by broadcastVoiceViewModel.uiState.collectAsStateWithLifecycle()
                BroadcastVoiceDialog(
                    state = voiceState,
                    onSelect = broadcastVoiceViewModel::select,
                    onSelectNewsVoice = broadcastVoiceViewModel::selectNewsVoice,
                    onChattiness = broadcastVoiceViewModel::setChattiness,
                    onAnnouncerVolume = broadcastVoiceViewModel::setAnnouncerVolume,
                    onAnnouncerSpeed = broadcastVoiceViewModel::setAnnouncerSpeed,
                    onNewsSpeed = broadcastVoiceViewModel::setNewsSpeed,
                    onChooseJinglesFolder = { jinglesFolderLauncher.launch(null) },
                    onJingleEnabled = broadcastVoiceViewModel::setJingleEnabled,
                    onJingleEvery = broadcastVoiceViewModel::setJingleEvery,
                    onDjHooksEnabled = broadcastVoiceViewModel::setDjHooksEnabled,
                    onNewsEnabled = broadcastVoiceViewModel::setNewsEnabled,
                    onNewsQuietHours = broadcastVoiceViewModel::setNewsQuietHours,
                    onNewsQuietStart = broadcastVoiceViewModel::setNewsQuietStart,
                    onNewsQuietEnd = broadcastVoiceViewModel::setNewsQuietEnd,
                    onDownloadStock = broadcastVoiceViewModel::downloadStock,
                    onImport = {
                        importVoiceLauncher.launch(
                            arrayOf("application/zip", "application/octet-stream"),
                        )
                    },
                    onRemovePersonal = broadcastVoiceViewModel::removePersonal,
                    onDismiss = {
                        broadcastVoiceViewModel.dismissInstallResult()
                        broadcastVoiceOpen = false
                    },
                )
            }

            if (aboutOpen) {
                AboutDialog(onDismiss = { aboutOpen = false })
            }

            if (backupOpen) {
                val backupResult by backupViewModel.result.collectAsStateWithLifecycle()
                BackupDialog(
                    result = backupResult,
                    onExport = {
                        exportBackupLauncher.launch("sleepradio-backup-${System.currentTimeMillis()}.zip")
                    },
                    onRestore = {
                        importBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                    },
                    onDismiss = {
                        backupViewModel.dismissResult()
                        backupOpen = false
                    },
                )
            }

            state.pickerForSlot?.let { slotIndex ->
                SourcePickerDialog(
                    stations = state.stations,
                    customStationIds = state.customStationIds,
                    folderAlbums = state.folderAlbums,
                    musicFolderChosen = state.musicFolderChosen,
                    audiobooks = state.audiobooks,
                    audiobooksFolderChosen = state.audiobooksFolderChosen,
                    podcastFeeds = state.podcastFeeds,
                    onPickPodcast = { playerViewModel.assignPodcastToSlot(slotIndex, it) },
                    onPickStation = { playerViewModel.assignStationToSlot(slotIndex, it) },
                    onRemoveStation = playerViewModel::removeStation,
                    onAddManual = { addStationOpen = true },
                    onBrowseDirectory = { directoryAssignSlot = slotIndex; directoryOpen = true },
                    onPickBroadcast = { playerViewModel.assignBroadcastToSlot(slotIndex) },
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
    onNowPlaying: () -> Unit,
    onAmbient: () -> Unit,
    onSleep: () -> Unit,
    onRadioStations: () -> Unit,
    onPodcasts: () -> Unit,
    onBroadcastVoice: () -> Unit,
    onVuSync: () -> Unit,
    onLampBrightness: () -> Unit,
    onChannelButtons: () -> Unit,
    onScreenRotation: () -> Unit,
    onAbout: () -> Unit,
    onBackup: () -> Unit,
    onTtsTest: (() -> Unit)? = null,
) {
    ModalDrawerSheet(
        modifier = Modifier.drawWithContent {
            drawContent()
            // a chrome edge down the right-hand side of the faceplate
            val edge = 4.dp.toPx()
            drawRect(
                Brush.horizontalGradient(
                    listOf(Color(0xFF3A342B), Color(0xFFE6DCC8), Color(0xFF8F8676), Color(0xFF15110C)),
                    startX = size.width - edge, endX = size.width,
                ),
                topLeft = Offset(size.width - edge, 0f),
                size = Size(edge, size.height),
            )
        },
        drawerContainerColor = Color(0xFF1B1611),
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
    ) {
        Column(
            Modifier
                .safeDrawingPadding()
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // the engraved nameplate
            Text(
                "SLEEPRADIO",
                style = MaterialTheme.typography.titleLarge.copy(
                    letterSpacing = 4.sp,
                    shadow = Shadow(Color.Black, Offset(0f, 2f), 0f),
                ),
                color = Color(0xFFE0A64C),
                modifier = Modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 8.dp),
            )
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 10.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFFB9AC97), Color(0xFFE6DCC8), Color(0x00B9AC97)),
                        ),
                    ),
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
            NavigationDrawerItem(
                label = { Text("Podcasts") },
                selected = false,
                onClick = onPodcasts,
            )
            NavigationDrawerItem(
                label = { Text("Broadcast voice") },
                selected = false,
                onClick = onBroadcastVoice,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            NavigationDrawerItem(
                label = { Text("VU meter sync") },
                selected = false,
                onClick = onVuSync,
            )
            NavigationDrawerItem(
                label = { Text("Lamp brightness") },
                selected = false,
                onClick = onLampBrightness,
            )
            NavigationDrawerItem(
                label = { Text("Channel buttons") },
                selected = false,
                onClick = onChannelButtons,
            )
            NavigationDrawerItem(
                label = { Text("Screen rotation") },
                selected = false,
                onClick = onScreenRotation,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            NavigationDrawerItem(
                label = { Text("Backup & restore") },
                selected = false,
                onClick = onBackup,
            )
            NavigationDrawerItem(
                label = { Text("About") },
                selected = false,
                onClick = onAbout,
            )
            if (onTtsTest != null) {
                NavigationDrawerItem(
                    label = { Text("▶ Speak test line (debug)") },
                    selected = false,
                    onClick = onTtsTest,
                )
            }
        }
    }
}

@Composable
private fun BackupDialog(
    result: BackupViewModel.Result,
    onExport: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    RadioDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { RadioTextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Backup & restore") },
        text = {
            Column {
                Text(
                    "Saves your settings, source slots, audiobook/podcast progress " +
                        "and any installed voice packs into one file you choose where " +
                        "to keep. Folder permissions (Music/Audiobooks/Jingles) can't " +
                        "be backed up — Android revokes those on reinstall — so " +
                        "you'll need to re-pick them once after a restore.",
                    fontSize = 12.sp,
                )
                Spacer(Modifier.padding(6.dp))
                when (result) {
                    BackupViewModel.Result.Working ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.padding(end = 10.dp))
                            Text("Working…", fontSize = 13.sp)
                        }
                    BackupViewModel.Result.ExportDone -> Text("Backup saved ✓", fontSize = 13.sp)
                    is BackupViewModel.Result.RestoreDone -> Column {
                        Text("Restored ✓", fontSize = 13.sp)
                        if (result.voicesRestored.isNotEmpty()) {
                            Text(
                                "Voices restored: ${result.voicesRestored.joinToString()}",
                                fontSize = 12.sp,
                            )
                        }
                        if (result.foldersToRepick.isNotEmpty()) {
                            Text(
                                "Re-pick these folders: ${result.foldersToRepick.joinToString()}",
                                fontSize = 12.sp,
                            )
                        }
                    }
                    is BackupViewModel.Result.Failed ->
                        Text("Failed: ${result.reason}", fontSize = 13.sp)
                    BackupViewModel.Result.Idle -> Unit
                }
                Spacer(Modifier.padding(6.dp))
                RadioTextButton(onClick = onExport) { Text("Create backup…") }
                RadioTextButton(onClick = onRestore) { Text("Restore backup…") }
            }
        },
    )
}

/** A "From 11:00 PM" style button that opens the system time picker; [minutes] is since midnight. */
@Composable
private fun QuietTimeButton(label: String, minutes: Int, enabled: Boolean, onPick: (Int) -> Unit) {
    val context = LocalContext.current
    val text = remember(minutes) {
        LocalTime.ofSecondOfDay(minutes.coerceIn(0, 24 * 60 - 1) * 60L)
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    }
    RadioTextButton(
        enabled = enabled,
        onClick = {
            TimePickerDialog(
                context,
                { _, hour, minute -> onPick(hour * 60 + minute) },
                minutes / 60,
                minutes % 60,
                DateFormat.is24HourFormat(context),
            ).show()
        },
    ) { Text("$label $text", fontSize = 13.sp) }
}

/**
 * The credit the BBC's RSS terms of use (section 15) ask for wherever its headlines are
 * used: "BBC News" / bbc.co.uk/news as text plus a hyperlink. Plain text, no BBC logo.
 */
@Composable
private fun BbcNewsCredit(fontSize: Int) {
    val uriHandler = LocalUriHandler.current
    Column {
        Text("News headlines: BBC News", fontSize = fontSize.sp)
        Text(
            "bbc.co.uk/news",
            fontSize = fontSize.sp,
            textDecoration = TextDecoration.Underline,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable {
                runCatching { uriHandler.openUri("https://www.bbc.co.uk/news") }
            },
        )
    }
}

/**
 * Lamp brightness: a slider with a live preview of a lit channel key and a lit PLAY button, so the
 * effect is visible while dragging. The setting is saved when the slider is released.
 */
/**
 * Channel buttons: four at a time (2x2, two pages) or one big button at a time (swipe sideways),
 * the latter for people who find the small buttons hard to see. Takes effect at once, behind the dialog.
 */
@Composable
private fun ChannelButtonsDialog(current: ChannelButtonMode, onPick: (ChannelButtonMode) -> Unit, onDismiss: () -> Unit) {
    RadioDialog(
        onDismissRequest = onDismiss,
        confirmButton = { RadioTextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Channel buttons") },
        text = {
            Column {
                VoiceOptionRow(
                    selected = current == ChannelButtonMode.GRID,
                    title = "Four at a time",
                    subtitle = "Eight channels as two pages of four buttons",
                    onClick = { onPick(ChannelButtonMode.GRID) },
                )
                VoiceOptionRow(
                    selected = current == ChannelButtonMode.BIG,
                    title = "One big button",
                    subtitle = "A single large button at a time. Swipe sideways to move to the next channel. Easiest to see.",
                    onClick = { onPick(ChannelButtonMode.BIG) },
                )
            }
        },
    )
}

@Composable
private fun ScreenRotationDialog(current: ScreenRotation, onPick: (ScreenRotation) -> Unit, onDismiss: () -> Unit) {
    RadioDialog(
        onDismissRequest = onDismiss,
        confirmButton = { RadioTextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Screen rotation") },
        text = {
            Column {
                VoiceOptionRow(
                    selected = current == ScreenRotation.AUTO,
                    title = "Auto-rotate",
                    subtitle = "Follow the phone's own rotation setting",
                    onClick = { onPick(ScreenRotation.AUTO) },
                )
                VoiceOptionRow(
                    selected = current == ScreenRotation.PORTRAIT,
                    title = "Always portrait",
                    subtitle = "Stay upright, even when the phone is turned",
                    onClick = { onPick(ScreenRotation.PORTRAIT) },
                )
                VoiceOptionRow(
                    selected = current == ScreenRotation.LANDSCAPE,
                    title = "Always landscape",
                    subtitle = "Stay sideways, even when the phone is held upright",
                    onClick = { onPick(ScreenRotation.LANDSCAPE) },
                )
            }
        },
    )
}

@Composable
private fun LampBrightnessDialog(current: Float, onCommit: (Float) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableFloatStateOf(current) }
    RadioDialog(
        onDismissRequest = onDismiss,
        confirmButton = { RadioTextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Lamp brightness") },
        text = {
            Column {
                Text(
                    "How brightly the lit lamps and windows on the keys shine. Turn it down for a dark bedroom.",
                    fontSize = 13.sp,
                )
                Spacer(Modifier.padding(6.dp))
                CompositionLocalProvider(LocalLampBrightness provides value) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChannelKey(
                            number = 1,
                            label = "Preview",
                            active = true,
                            playing = true,
                            contentDescription = "Preview of a lit channel key",
                            onClick = {},
                            onLongClick = {},
                            modifier = Modifier.weight(1f).height(70.dp),
                        )
                        RoundKey(
                            glyph = RoundGlyph.PAUSE,
                            size = 48.dp,
                            lit = true,
                            contentDescription = "Preview of a lit play button",
                            onClick = {},
                        )
                    }
                }
                Spacer(Modifier.padding(4.dp))
                Text("Brightness  ${(value * 100).toInt()}%", fontSize = 13.sp)
                RadioSlider(
                    value = value,
                    onValueChange = { value = it },
                    onValueChangeFinished = { onCommit(value) },
                    valueRange = 0.2f..1f,
                    steps = 15,
                )
            }
        },
    )
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }
    RadioDialog(
        onDismissRequest = onDismiss,
        confirmButton = { RadioTextButton(onClick = onDismiss) { Text("OK") } },
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
                Spacer(Modifier.padding(6.dp))
                BbcNewsCredit(fontSize = 12)
            }
        },
    )
}

@Composable
private fun BroadcastVoiceDialog(
    state: BroadcastVoiceViewModel.UiState,
    onSelect: (String) -> Unit,
    onSelectNewsVoice: (String) -> Unit,
    onChattiness: (Chattiness) -> Unit,
    onAnnouncerVolume: (Float) -> Unit,
    onAnnouncerSpeed: (Float) -> Unit,
    onNewsSpeed: (Float) -> Unit,
    onChooseJinglesFolder: () -> Unit,
    onJingleEnabled: (Boolean) -> Unit,
    onJingleEvery: (Int) -> Unit,
    onDjHooksEnabled: (Boolean) -> Unit,
    onNewsEnabled: (Boolean) -> Unit,
    onNewsQuietHours: (Boolean) -> Unit,
    onNewsQuietStart: (Int) -> Unit,
    onNewsQuietEnd: (Int) -> Unit,
    onDownloadStock: () -> Unit,
    onImport: () -> Unit,
    onRemovePersonal: () -> Unit,
    onDismiss: () -> Unit,
) {
    RadioDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { RadioTextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Broadcast voice") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "The DJ reads short links between tracks. Fully offline — a voice " +
                        "is only ever installed from a file or a plain download, never uploaded.",
                    fontSize = 12.sp,
                )
                Spacer(Modifier.padding(4.dp))
                SectionHeader("DJ VOICE")
                VoiceOptionRow(
                    selected = state.selected == BROADCAST_VOICE_OFF,
                    title = "Off",
                    subtitle = "Music only — no spoken links",
                    onClick = { onSelect(BROADCAST_VOICE_OFF) },
                )
                VoiceOptionRow(
                    selected = state.selected == BROADCAST_VOICE_STOCK,
                    title = "Stock voice",
                    subtitle = if (state.stockInstalled) "Ready" else "Not installed",
                    enabled = state.stockInstalled,
                    onClick = { onSelect(BROADCAST_VOICE_STOCK) },
                )
                if (state.personalInstalled) {
                    VoiceOptionRow(
                        selected = state.selected == BROADCAST_VOICE_PERSONAL,
                        title = "My voice",
                        subtitle = "Imported on this device",
                        onClick = { onSelect(BROADCAST_VOICE_PERSONAL) },
                    )
                }

                Spacer(Modifier.padding(4.dp))
                SectionHeader("NEWS")
                Text(
                    "Reads a short bulletin between tracks around the top of the hour " +
                        "(top stories) and half past (lighter stories). Needs a data " +
                        "connection; with this off the Broadcast never goes online. " +
                        "Never plays while the sleep timer is winding down.",
                    fontSize = 11.sp,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("News bulletins", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    RadioSwitch(checked = state.newsEnabled, onCheckedChange = onNewsEnabled)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Quiet hours (no news)",
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    RadioSwitch(
                        checked = state.newsQuietHours,
                        onCheckedChange = onNewsQuietHours,
                        enabled = state.newsEnabled,
                    )
                }
                val quietPickersOn = state.newsEnabled && state.newsQuietHours
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuietTimeButton("From", state.newsQuietStartMin, quietPickersOn, onNewsQuietStart)
                    QuietTimeButton("Until", state.newsQuietEndMin, quietPickersOn, onNewsQuietEnd)
                }
                if (state.newsQuietStartMin == state.newsQuietEndMin) {
                    Text("Start and end are the same, so there is no quiet period.", fontSize = 11.sp)
                }
                BbcNewsCredit(fontSize = 11)

                Spacer(Modifier.padding(4.dp))
                SectionHeader("NEWS READER VOICE")
                VoiceOptionRow(
                    selected = state.newsVoice == NEWS_VOICE_SAME,
                    title = "Same as the DJ",
                    subtitle = "Uses the DJ voice above",
                    onClick = { onSelectNewsVoice(NEWS_VOICE_SAME) },
                )
                VoiceOptionRow(
                    selected = state.newsVoice == BROADCAST_VOICE_STOCK,
                    title = "Stock voice",
                    subtitle = if (state.stockInstalled) "The downloaded default voice" else "Not installed",
                    enabled = state.stockInstalled,
                    onClick = { onSelectNewsVoice(BROADCAST_VOICE_STOCK) },
                )
                if (state.personalInstalled) {
                    VoiceOptionRow(
                        selected = state.newsVoice == BROADCAST_VOICE_PERSONAL,
                        title = "My voice",
                        subtitle = "Imported on this device",
                        onClick = { onSelectNewsVoice(BROADCAST_VOICE_PERSONAL) },
                    )
                }
                Text(
                    "Reading speed  ${(state.newsSpeed * 100).toInt()}%" +
                        when {
                            state.newsSpeed < 0.98f -> "  (slower)"
                            state.newsSpeed > 1.02f -> "  (faster)"
                            else -> "  (natural)"
                        },
                    fontSize = 11.sp,
                )
                RadioSlider(
                    value = state.newsSpeed,
                    onValueChange = onNewsSpeed,
                    valueRange = 0.6f..1.2f,
                    steps = 11,
                )

                Spacer(Modifier.padding(4.dp))
                SectionHeader("CHATTINESS")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Chattiness.entries.forEach { c ->
                        val on = state.chattiness == c
                        Text(
                            text = when (c) {
                                Chattiness.MAXIMUM -> "Maximum"
                                Chattiness.CHATTY -> "Chatty"
                                Chattiness.BALANCED -> "Balanced"
                                Chattiness.MINIMAL -> "Minimal"
                            },
                            fontSize = 12.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .clickable { onChattiness(c) }
                                .padding(vertical = 8.dp, horizontal = 2.dp),
                        )
                    }
                }
                Text(
                    when (state.chattiness) {
                        Chattiness.MAXIMUM ->
                            "A link before every track — back-announces what just played and " +
                                "introduces what's next."
                        else -> "A spoken link every ${state.chattiness.tracksPerLink} tracks."
                    },
                    fontSize = 11.sp,
                )

                Spacer(Modifier.padding(4.dp))
                SectionHeader("ANNOUNCER")
                Text(
                    "Volume  ${(state.announcerVolume * 100).toInt()}%  (of the VOL knob)",
                    fontSize = 11.sp,
                )
                RadioSlider(
                    value = state.announcerVolume,
                    onValueChange = onAnnouncerVolume,
                    valueRange = 0f..1f,
                )
                Text(
                    "Speed  ${(state.announcerSpeed * 100).toInt()}%" +
                        when {
                            state.announcerSpeed < 0.98f -> "  (slower)"
                            state.announcerSpeed > 1.02f -> "  (faster)"
                            else -> "  (natural)"
                        },
                    fontSize = 11.sp,
                )
                RadioSlider(
                    value = state.announcerSpeed,
                    onValueChange = onAnnouncerSpeed,
                    valueRange = 0.7f..1.3f,
                    steps = 11,
                )

                Spacer(Modifier.padding(4.dp))
                SectionHeader("JINGLES")
                Text(
                    "Drop your own jingle files in between tracks. Pick a folder of " +
                        "short audio clips; they play shuffled.",
                    fontSize = 11.sp,
                )
                RadioTextButton(onClick = onChooseJinglesFolder) {
                    Text(
                        if (state.jinglesFolderSet) "Change jingles folder…"
                        else "Choose jingles folder…",
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Play jingles" +
                            if (!state.jinglesFolderSet) "  (choose a folder first)" else "",
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    RadioSwitch(
                        checked = state.jingleEnabled,
                        onCheckedChange = onJingleEnabled,
                        enabled = state.jinglesFolderSet,
                    )
                }
                if (state.jingleEnabled && state.jinglesFolderSet) {
                    Text(
                        "Every ${state.jingleEvery} track" +
                            if (state.jingleEvery == 1) "" else "s",
                        fontSize = 11.sp,
                    )
                    RadioSlider(
                        value = state.jingleEvery.toFloat(),
                        onValueChange = { onJingleEvery(it.roundToInt()) },
                        valueRange = 1f..10f,
                        steps = 8,
                    )
                }

                Spacer(Modifier.padding(4.dp))
                SectionHeader("70S DJ HOOKS")
                Text(
                    "Opens each track link with a cheeky 1970s-radio line before naming " +
                        "the next song. Bundled with the app — offline, no AI. Takes " +
                        "priority over AI commentary.",
                    fontSize = 11.sp,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("70s DJ hooks", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    RadioSwitch(checked = state.djHooksEnabled, onCheckedChange = onDjHooksEnabled)
                }

                Spacer(Modifier.padding(6.dp))
                when (val s = state.install) {
                    is VoicePackInstaller.InstallState.Working -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.padding(end = 10.dp))
                        Text(
                            if (s.pct < 0) "Installing…" else "Installing… ${s.pct}%",
                            fontSize = 13.sp,
                        )
                    }
                    is VoicePackInstaller.InstallState.Failed ->
                        Text("Couldn't install: ${s.reason}", fontSize = 13.sp)
                    VoicePackInstaller.InstallState.Ready ->
                        Text("Installed ✓", fontSize = 13.sp)
                    VoicePackInstaller.InstallState.Idle -> Unit
                }

                if (!state.stockInstalled) {
                    RadioTextButton(onClick = onDownloadStock) { Text("Download stock voice") }
                }
                RadioTextButton(onClick = onImport) { Text("Import a voice…") }
                if (state.personalInstalled) {
                    RadioTextButton(onClick = onRemovePersonal) { Text("Remove my voice") }
                }
            }
        },
    )
}

@Composable
private fun VoiceOptionRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioLamp(selected = selected, onClick = onClick, enabled = enabled)
        Column(Modifier.padding(start = 4.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(subtitle, fontSize = 12.sp, maxLines = 3)
        }
    }
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
    RadioDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { RadioTextButton(onClick = onDismiss) { Text("Close") } },
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
private fun SourcePickerDialog(
    stations: List<RadioStation>,
    customStationIds: Set<String>,
    folderAlbums: List<FolderAlbum>,
    musicFolderChosen: Boolean,
    audiobooks: List<Audiobook>,
    audiobooksFolderChosen: Boolean,
    podcastFeeds: List<PodcastFeed>,
    onPickPodcast: (PodcastFeed) -> Unit,
    onPickStation: (RadioStation) -> Unit,
    onRemoveStation: (String) -> Unit,
    onAddManual: () -> Unit,
    onBrowseDirectory: () -> Unit,
    onPickBroadcast: () -> Unit,
    onPickFolderAlbum: (FolderAlbum) -> Unit,
    onPickAudiobook: (Audiobook) -> Unit,
    onChooseAudiobooksFolder: () -> Unit,
    onChooseMusicFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    RadioDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { RadioTextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Assign a source") },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                item { SectionHeader("BROADCAST") }
                item {
                    PickerRow(
                        primary = "📻  SleepRadio broadcast",
                        secondary = if (musicFolderChosen) {
                            "Auto-DJ over your music folder"
                        } else {
                            "Choose a music folder first"
                        },
                        onClick = { if (musicFolderChosen) onPickBroadcast() },
                    )
                    HorizontalDivider()
                }

                item { SectionHeader("PODCASTS") }
                if (podcastFeeds.isEmpty()) {
                    item {
                        Text(
                            "Subscribe to a show from the Podcasts drawer entry first.",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                } else {
                    items(podcastFeeds, key = { "pod_${it.id}" }) { feed ->
                        PickerRow(feed.title, "Podcast — resumes the latest episode") {
                            onPickPodcast(feed)
                        }
                        HorizontalDivider()
                    }
                }

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
    RadioDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { RadioTextButton(onClick = onDismiss) { Text("Cancel") } },
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
    RadioDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { RadioTextButton(onClick = onDismiss) { Text("Done") } },
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
                    RadioSlider(
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
            RadioTextButton(onClick = onClear) { Text("Clear") }
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
    // an engraved section plate: small amber capitals, widely spaced
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.4.sp),
        color = Color(0xFFE0A64C),
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
            RadioTextButton(onClick = onRemove) { Text("Remove") }
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
    RadioDialog(
        onDismissRequest = onDismiss,
        dismissButton = { RadioTextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            RadioTextButton(
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
    RadioDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { RadioTextButton(onClick = onDismiss) { Text("Close") } },
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
                    RadioTextButton(onClick = { onSearch(query) }) { Text("Go") }
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

// --- Podcasts (Phase 17) ---------------------------------------------------

@Composable
private fun PodcastsDialog(
    feeds: List<PodcastFeed>,
    onOpenFeed: (PodcastFeed) -> Unit,
    onUnsubscribe: (String) -> Unit,
    onAddByUrl: () -> Unit,
    onBrowseDirectory: () -> Unit,
    onDismiss: () -> Unit,
) {
    RadioDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { RadioTextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Podcasts") },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                item {
                    PickerRow("＋  Add a feed by URL…", "Paste a show's RSS feed URL", onAddByUrl)
                    HorizontalDivider()
                }
                item {
                    PickerRow(
                        "⌕  Search a podcast directory…",
                        "Search Apple's podcast directory",
                        onBrowseDirectory,
                    )
                    HorizontalDivider()
                }
                item { SectionHeader("SUBSCRIBED") }
                if (feeds.isEmpty()) {
                    item {
                        Text(
                            "No shows yet — search the directory or add a feed URL above.",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
                items(feeds, key = { it.id }) { feed ->
                    PodcastFeedRow(feed = feed, onClick = { onOpenFeed(feed) }, onRemove = { onUnsubscribe(feed.id) })
                    HorizontalDivider()
                }
            }
        },
    )
}

@Composable
private fun PodcastFeedRow(feed: PodcastFeed, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PodcastThumbnail(feed.artworkUrl)
        Spacer(Modifier.padding(6.dp))
        Column(
            Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
        ) {
            Text(feed.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Podcast", fontSize = 12.sp, maxLines = 1)
        }
        RadioTextButton(onClick = onRemove) { Text("Remove") }
    }
}

@Composable
private fun PodcastThumbnail(artworkUrl: String?, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val art = rememberRemoteImage(artworkUrl)
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(androidx.compose.ui.graphics.Color(0xFF2A2E38)),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text("🎙", fontSize = (size.value / 2.5f).sp)
        }
    }
}

@Composable
private fun PodcastEpisodesDialog(
    feed: PodcastFeed,
    episodes: List<PodcastEpisode>,
    loading: Boolean,
    error: Boolean,
    progress: Map<String, PodcastProgress>,
    onPlay: (PodcastEpisode) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    RadioDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            Row {
                RadioTextButton(onClick = onBack) { Text("Back") }
                RadioTextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        title = { Text(feed.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            when {
                loading -> Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }

                error -> Text(
                    "Couldn't load this show's episodes — check the connection and try again.",
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )

                episodes.isEmpty() -> Text(
                    "No episodes found in this feed.",
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )

                else -> LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    items(episodes, key = { it.guid }) { episode ->
                        PodcastEpisodeRow(episode, progress[episode.guid]) { onPlay(episode) }
                        HorizontalDivider()
                    }
                }
            }
        },
    )
}

@Composable
private fun PodcastEpisodeRow(episode: PodcastEpisode, progress: PodcastProgress?, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(episode.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val meta = listOfNotNull(
            episode.durationMs?.let { formatTime(it) },
            when {
                progress?.completed == true -> "Played"
                progress != null && progress.positionMs > 0L ->
                    "Resume at ${formatTime(progress.positionMs)}"
                else -> null
            },
        ).joinToString(" · ")
        if (meta.isNotEmpty()) {
            Text(meta, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun AddPodcastDialog(onAdd: (url: String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    RadioDialog(
        onDismissRequest = onDismiss,
        dismissButton = { RadioTextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            RadioTextButton(
                enabled = url.isNotBlank(),
                onClick = { onAdd(url); onDismiss() },
            ) { Text("Add") }
        },
        title = { Text("Add a podcast feed") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("RSS feed URL") },
                placeholder = { Text("https://…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (url.isNotBlank()) { onAdd(url); onDismiss() } }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun PodcastDirectoryDialog(
    results: List<PodcastFeed>,
    searching: Boolean,
    onSearch: (String) -> Unit,
    onPick: (PodcastFeed) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    RadioDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { RadioTextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Podcast directory") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search shows") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                        modifier = Modifier.weight(1f),
                    )
                    RadioTextButton(onClick = { onSearch(query) }) { Text("Go") }
                }
                Spacer(Modifier.padding(4.dp))
                when {
                    searching -> Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) { CircularProgressIndicator() }

                    results.isEmpty() -> Text(
                        "Type a show name and tap Go.",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )

                    else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(results, key = { it.id }) { feed ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                PodcastThumbnail(feed.artworkUrl, size = 32.dp)
                                Spacer(Modifier.padding(6.dp))
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .clickable(onClick = { onPick(feed) })
                                        .padding(vertical = 12.dp),
                                ) {
                                    Text(
                                        feed.title,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        feed.feedUrl,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
    )
}
