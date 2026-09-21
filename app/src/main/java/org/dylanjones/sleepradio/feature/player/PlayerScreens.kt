package org.dylanjones.sleepradio.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.dylanjones.sleepradio.core.audio.BinauralPreset
import org.dylanjones.sleepradio.core.audio.NoiseColor
import org.dylanjones.sleepradio.core.audio.VuLevels
import org.dylanjones.sleepradio.core.data.PRESET_COUNT
import org.dylanjones.sleepradio.core.data.ChannelButtonMode
import org.dylanjones.sleepradio.core.data.PRESET_PAGES
import org.dylanjones.sleepradio.core.data.PRESETS_PER_PAGE
import org.dylanjones.sleepradio.core.data.SourceSlot
import org.dylanjones.sleepradio.core.data.SourceType
import org.dylanjones.sleepradio.core.design.LocalAppSkin
import org.dylanjones.sleepradio.core.design.LocalChannelButtonMode
import org.dylanjones.sleepradio.core.design.ChannelKey
import org.dylanjones.sleepradio.core.design.KeyIcon
import org.dylanjones.sleepradio.core.design.RoundGlyph
import org.dylanjones.sleepradio.core.design.RoundKey
import org.dylanjones.sleepradio.core.design.ToggleKey
import org.dylanjones.sleepradio.core.design.RotaryKnob
import org.dylanjones.sleepradio.core.design.SkinPanel

/** UI callbacks for the player screen. */
class PlayerActions(
    val onMenu: () -> Unit,
    val onPresetClick: (Int) -> Unit,
    val onPresetLongClick: (Int) -> Unit,
    val onPlayPause: () -> Unit,
    /** Next track / +1 min while an audiobook plays. */
    val onNext: () -> Unit,
    /** Previous track / −1 min while an audiobook plays. */
    val onPrevious: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onVolumeChange: (Float) -> Unit,
    val onBalanceChange: (Float) -> Unit,
    /** SLEEP tile tap — start the timer, or cancel a running one. */
    val onSleepTap: () -> Unit,
    /** SLEEP tile long-press — open the duration picker. */
    val onSleepDurationPick: () -> Unit,
    /** NOISE tile tap — toggle Channel B on/off. */
    val onNoiseToggle: () -> Unit,
    /** NOISE tile long-press — open the spectrum picker. */
    val onNoiseColorPick: () -> Unit,
)

private val placeholderFreqs = listOf("94.7", "101.3", "88.5", "106.1", "98.9", "91.5", "104.9", "89.7")

/**
 * The player layout — the same for every source type, so switching between them never moves
 * anything: header, now-playing block, preset row, half-height SLEEP/NOISE tiles, a pair of
 * analogue VU meters, and one control row (RWD · VOL · PAUSE · BAL · FFWD). See
 * RETROSYNC_PLAN.md §3.
 */
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
    vu: StateFlow<VuLevels> = remember { MutableStateFlow(VuLevels.SILENT) },
) {
    val pb = state.playback
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopPanel(
            state = pb,
            starting = state.broadcastStarting,
            onMenu = actions.onMenu,
            onSeek = actions.onSeek,
        )
        Spacer(Modifier.height(14.dp))

        SkinPanel(
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            PresetRow(state, actions, Modifier.fillMaxWidth().weight(1f))
            Spacer(Modifier.height(8.dp))
            AmbientTiles(state, actions, Modifier.fillMaxWidth().weight(0.42f))
            Spacer(Modifier.height(8.dp))
            val levels by vu.collectAsStateWithLifecycle()
            VuMeterPair(levels, Modifier.fillMaxWidth().weight(0.6f))
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val skipTransport = pb.isAudiobook || pb.isPodcast
            RoundKey(
                glyph = RoundGlyph.PREVIOUS,
                size = 48.dp,
                lit = false,
                contentDescription = if (skipTransport) "Back one minute" else "Previous",
                onClick = actions.onPrevious,
                enabled = skipTransport || pb.hasPrevious,
            )
            RotaryKnob("VOL", state.volume, actions.onVolumeChange, size = 52.dp)
            // PLAY: the symbol shows the action (pause while playing) and the ring lights while playing.
            RoundKey(
                glyph = if (pb.isPlaying) RoundGlyph.PAUSE else RoundGlyph.PLAY,
                size = 62.dp,
                lit = pb.isPlaying,
                contentDescription = if (pb.isPlaying) "Pause" else "Play",
                onClick = actions.onPlayPause,
            )
            RotaryKnob("BAL", state.balance, actions.onBalanceChange, size = 52.dp)
            RoundKey(
                glyph = RoundGlyph.NEXT,
                size = 48.dp,
                lit = false,
                contentDescription = if (skipTransport) "Forward one minute" else "Next",
                onClick = actions.onNext,
                enabled = skipTransport || pb.hasNext,
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * The channel buttons, swiped left and right. Two modes (the "Channel buttons" setting): four at a
 * time in a 2 x 2 block (two pages of four), or ONE BIG button at a time (eight pages) for people who
 * find the small buttons hard to see. Either way the block takes the same space, and the page markers
 * underneath always take the same fixed height. If the playing channel is on a page you are NOT
 * looking at, its marker shows ▶ / ❚❚ instead of a dot, so it is never lost off-screen. The pager
 * opens on the page holding the playing channel.
 */
@Composable
private fun PresetRow(state: PlayerUiState, actions: PlayerActions, modifier: Modifier) {
    val mode = LocalChannelButtonMode.current
    val scope = rememberCoroutineScope()
    val colors = LocalAppSkin.current.colors

    // Per channel: is it the loaded source, and is it running? (A Broadcast that is tuning in
    // already counts as running.)
    val active = BooleanArray(PRESET_COUNT)
    val playing = BooleanArray(PRESET_COUNT)
    for (i in 0 until PRESET_COUNT) {
        val slot = state.presets.getOrNull(i)
        val tuningIn = slot?.type == SourceType.BROADCAST && state.broadcastStarting
        active[i] = tuningIn || isActivePreset(slot, state.nowPlayingRef, state.playback)
        playing[i] = active[i] && (tuningIn || isPresetPlaying(state.playback, state.broadcastStarting))
    }
    val firstActive = (0 until PRESET_COUNT).firstOrNull { active[it] }

    // A new pager (opening on the playing channel's page) whenever the mode is switched.
    key(mode) {
        val pagerState = rememberPagerState(initialPage = mode.pageOf(firstActive ?: 0), pageCount = { mode.pageCount })
        val big = mode == ChannelButtonMode.BIG
        Column(modifier) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                pageSpacing = 10.dp,
            ) { page ->
                val channels = mode.channelsOnPage(page)
                if (big) {
                    // One big button filling the block; the artwork keeps its shape inside it.
                    val i = channels.first
                    PresetTile(
                        index = i,
                        slot = state.presets.getOrNull(i),
                        active = active[i],
                        playing = playing[i],
                        broadcastReady = state.broadcastReady,
                        modifier = Modifier.fillMaxSize(),
                        onClick = { actions.onPresetClick(i) },
                        onLongClick = { actions.onPresetLongClick(i) },
                        big = true,
                    )
                } else {
                    // Four wide buttons in a 2 x 2 block (the artwork keeps its shape inside each cell).
                    Column(
                        Modifier
                            .fillMaxSize()
                            .semantics { contentDescription = "Channels ${channels.first + 1} to ${channels.last + 1}" },
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        for (row in 0 until PRESETS_PER_PAGE / 2) {
                            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (col in 0 until 2) {
                                    val i = channels.first + row * 2 + col
                                    PresetTile(
                                        index = i,
                                        slot = state.presets.getOrNull(i),
                                        active = active[i],
                                        playing = playing[i],
                                        broadcastReady = state.broadcastReady,
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                        onClick = { actions.onPresetClick(i) },
                                        onLongClick = { actions.onPresetLongClick(i) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // Page markers: always laid out at a fixed height.
            Row(
                Modifier.fillMaxWidth().height(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(mode.pageCount) { page ->
                    val here = pagerState.currentPage == page
                    val range = mode.channelsOnPage(page)
                    val pageActive = range.any { active[it] }
                    val pagePlaying = range.any { playing[it] }
                    Box(
                        Modifier
                            .width(if (big) 26.dp else 28.dp)
                            .fillMaxHeight()
                            .clickable { scope.launch { pagerState.animateScrollToPage(page) } }
                            .semantics {
                                contentDescription = (if (big) "Channel ${range.first + 1}" else "Channels ${range.first + 1} to ${range.last + 1}") +
                                    if (here) ", showing" else ""
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (pageActive && !here) {
                            Text(
                                text = if (pagePlaying) "▶" else "❚❚",
                                color = colors.accent,
                                fontSize = if (big) 11.sp else 9.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        } else {
                            Box(
                                Modifier
                                    .size(if (here) (if (big) 11.dp else 8.dp) else (if (big) 8.dp else 6.dp))
                                    .clip(CircleShape)
                                    .background(if (here) colors.accent else colors.textDim),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AmbientTiles(
    state: PlayerUiState,
    actions: PlayerActions,
    modifier: Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val minutesLeft = ((state.sleepRemainingMs + 59_999L) / 60_000L).coerceAtLeast(0L)
        ToggleKey(
            icon = KeyIcon.MOON,
            label = "SLEEP\n${state.sleepDurationMin} MIN",
            // While counting down: just the minutes left, as big as the window allows — no word.
            bigLabel = if (state.sleepActive) "$minutesLeft" else null,
            on = state.sleepActive,
            contentDescription = if (state.sleepActive) {
                "Sleep timer running, ${formatTime(state.sleepRemainingMs)} left. Double tap to cancel."
            } else {
                "Sleep timer, ${state.sleepDurationMin} minutes. Double tap to start, long press to change."
            },
            onClick = actions.onSleepTap,
            onLongClick = actions.onSleepDurationPick,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        val binauralOn = state.binaural != BinauralPreset.OFF
        ToggleKey(
            icon = KeyIcon.WAVE,
            label = "NOISE\n" + ambientSummary(state.noiseEnabled, state.noiseColor, binauralOn),
            on = state.noiseEnabled || binauralOn,
            contentDescription = "Noise and binaural: " +
                ambientSummary(state.noiseEnabled, state.noiseColor, binauralOn) +
                ". Double tap to toggle noise, long press for the ambient mix.",
            onClick = actions.onNoiseToggle,
            onLongClick = actions.onNoiseColorPick,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

internal fun NoiseColor.label(): String = when (this) {
    NoiseColor.WHITE -> "WHITE"
    NoiseColor.PINK -> "PINK"
    NoiseColor.BROWN -> "BROWN"
    NoiseColor.BLUE -> "BLUE"
    NoiseColor.DEEP_SPACE -> "DEEP SPACE"
    NoiseColor.AMBIENT -> "AMBIENT"
}

/** NOISE-tile sub-label covering both ambient channels (long-press = config). */
private fun ambientSummary(noiseOn: Boolean, color: NoiseColor, binauralOn: Boolean): String = when {
    noiseOn && binauralOn -> "${color.label()} +β"
    noiseOn -> color.label()
    binauralOn -> "BEATS"
    else -> "OFF"
}

@Composable
private fun PresetTile(
    index: Int,
    slot: SourceSlot?,
    active: Boolean,
    playing: Boolean,
    broadcastReady: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    big: Boolean = false,
) {
    val showReadyDot = slot?.type == SourceType.BROADCAST && broadcastReady && !active
    val description = if (slot == null) {
        "Preset ${index + 1}, empty. Double tap to assign a source."
    } else {
        "Preset ${index + 1}, ${slot.label}" +
            when {
                active && playing -> ", playing. Double tap to pause."
                active -> ", paused. Double tap to resume."
                showReadyDot -> ", ready to start instantly. Double tap to play."
                else -> ". Double tap to play."
            } + " Long press to clear."
    }
    ChannelKey(
        number = index + 1,
        // A Broadcast that is ready to start instantly shows a dot before its name.
        label = (if (showReadyDot) "● " else "") + (slot?.label ?: "FM ${placeholderFreqs.getOrElse(index) { "—" }}"),
        active = active,
        playing = playing,
        contentDescription = description,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        empty = slot == null,
        big = big,
    )
}
