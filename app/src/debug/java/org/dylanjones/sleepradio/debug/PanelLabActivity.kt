package org.dylanjones.sleepradio.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.dylanjones.sleepradio.core.data.SourceSlot
import org.dylanjones.sleepradio.core.data.SourceType
import org.dylanjones.sleepradio.core.design.LocalLampBrightness
import org.dylanjones.sleepradio.core.design.SkinBackground
import org.dylanjones.sleepradio.core.design.StudioSkin
import org.dylanjones.sleepradio.feature.player.PlayerScreen
import org.dylanjones.sleepradio.feature.player.PlayerUiState
import org.dylanjones.sleepradio.feature.player.copy2
import org.dylanjones.sleepradio.feature.player.noopActions
import org.dylanjones.sleepradio.feature.player.previewState
import org.dylanjones.sleepradio.playback.PlaybackState
import org.dylanjones.sleepradio.ui.theme.SleepRadioTheme

/**
 * Debug-only, NO AUDIO: draws the real player screen from made-up playback state so the populated
 * looks (artist/track windows, gauge, slide rule, lit keys) can be checked on a device without
 * playing anything. Seeking (drag the slide rule) and PLAY work on the fake state.
 *
 *   adb shell am start -n org.dylanjones.sleepradio/.debug.PanelLabActivity \
 *       --es scenario broadcast|broadcast_dj|radio|radio_buffering|book|podcast|idle|starting \
 *       [--ef lamp 0.5] [--ei pos 83]
 */
class PanelLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val scenario = intent.getStringExtra("scenario") ?: "broadcast"
        val lamp = intent.getFloatExtra("lamp", 1f)
        val startSec = intent.getIntExtra("pos", 83)
        setContent {
            SleepRadioTheme {
                var positionMs by remember { mutableLongStateOf(startSec * 1000L) }
                var playing by remember { mutableStateOf(true) }
                val base = fake(scenario)
                val state = base.copy(
                    playback = base.playback.copy(
                        positionMs = if (base.playback.isRadio) 0L else positionMs,
                        isPlaying = playing && base.playback.isConnected,
                    ),
                )
                SkinBackground(StudioSkin) {
                    CompositionLocalProvider(LocalLampBrightness provides lamp) {
                        PlayerScreen(
                            state = state,
                            actions = noopActions.copy2(
                                onSeek = { positionMs = it },
                                onPlayPause = { playing = !playing },
                            ),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    private fun fake(scenario: String): PlayerUiState {
        val slots = listOf(
            SourceSlot(0, SourceType.BROADCAST, "broadcast", "SleepRadio broadcast", ""),
            SourceSlot(1, SourceType.RADIO, "http://radio", "BBC Radio 4", ""),
            SourceSlot(2, SourceType.AUDIOBOOK, "book1", "A Short History of Nearly Everything", ""),
            SourceSlot(3, SourceType.PODCAST, "pod1", "The Higherside Chats", ""),
        ) + List(4) { null }
        val (pb, ref) = when (scenario) {
            "broadcast" -> PlaybackState(
                isConnected = true, isPlaying = true, isBroadcast = true,
                artist = "Sergei Rachmaninoff", title = "Piano Concerto No. 3 in D minor, Op. 30", durationMs = 247_000,
            ) to "broadcast"
            "broadcast_dj" -> PlaybackState(
                isConnected = true, isPlaying = true, isBroadcast = true, djSpeaking = true,
                artist = "Bob Dylan", title = "Black Crow Blues", durationMs = 191_000,
            ) to "broadcast"
            "radio" -> PlaybackState(
                isConnected = true, isPlaying = true, isRadio = true, stationName = "BBC Radio 4", nowPlaying = "The World at One",
            ) to "http://radio"
            "radio_buffering" -> PlaybackState(
                isConnected = true, isBuffering = true, isRadio = true, stationName = "BBC Radio 4", nowPlaying = "The World at One",
            ) to "http://radio"
            "book" -> PlaybackState(
                isConnected = true, isPlaying = true, isAudiobook = true, queueSize = 20,
                artist = "Bill Bryson", title = "A Short History of Nearly Everything", durationMs = 1_560_000,
            ) to "book1"
            "podcast" -> PlaybackState(
                isConnected = true, isPlaying = true, isPodcast = true, queueSize = 1,
                artist = "The Higherside Chats", title = "Ancient Mysteries and Modern Minds", durationMs = 2_700_000,
            ) to "pod1"
            "starting" -> PlaybackState(isConnected = true) to null
            else -> PlaybackState(isConnected = true) to null // idle: nothing loaded
        }
        return previewState.copy(
            playback = pb,
            presets = slots,
            nowPlayingRef = ref,
            broadcastStarting = scenario == "starting",
        )
    }
}
