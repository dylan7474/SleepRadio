package org.dylanjones.sleepradio.feature.player

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.dylanjones.sleepradio.core.design.LocalAppSkin
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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> playerViewModel.onAudioPermissionResult(granted) }

    LaunchedEffect(state.hasAudioPermission) {
        if (!state.hasAudioPermission) {
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
        }
    }

    var menuOpen by remember { mutableStateOf(false) }

    val actions = PlayerActions(
        onMenu = { menuOpen = true },
        onBell = {},
        onPresetClick = playerViewModel::onPresetClicked,
        onPlayPause = playerViewModel::playPause,
        onNext = playerViewModel::next,
        onPrevious = playerViewModel::previous,
        onSeek = playerViewModel::seekTo,
        onVolumeChange = playerViewModel::onVolumeChange,
        onBalanceChange = playerViewModel::onBalanceChange,
        onSleepFractionChange = playerViewModel::onSleepFractionChange,
    )

    SkinBackground(skin) {
        when (skinId) {
            SkinId.NEON -> NeonPlayerScreen(state, actions, Modifier.fillMaxSize())
            SkinId.INDUSTRIAL -> IndustrialPlayerScreen(state, actions, Modifier.fillMaxSize())
        }

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

        state.pickerForSlot?.let { slot ->
            AlbumPickerDialog(
                albums = state.albums,
                onPick = { playerViewModel.assignPresetAndPlay(slot, it) },
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
private fun AlbumPickerDialog(
    albums: List<org.dylanjones.sleepradio.media.Album>,
    onPick: (org.dylanjones.sleepradio.media.Album) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Assign a source") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(albums, key = { it.id }) { album ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(album) }
                            .padding(vertical = 12.dp),
                    ) {
                        Text(album.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            "${album.artist} · ${album.trackCount} tracks",
                            fontSize = 12.sp,
                            maxLines = 1,
                            textAlign = TextAlign.Start,
                        )
                    }
                    HorizontalDivider()
                }
            }
        },
    )
}
