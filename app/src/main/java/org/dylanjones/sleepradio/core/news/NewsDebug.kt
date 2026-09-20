package org.dylanjones.sleepradio.core.news

import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dylanjones.sleepradio.BuildConfig
import org.dylanjones.sleepradio.core.tts.DjVoicePlayer
import org.dylanjones.sleepradio.core.tts.OfflineTtsEngine
import org.dylanjones.sleepradio.core.tts.VoicePackResolver
import java.time.LocalTime

/** The two TEMPORARY drawer test buttons for the news feature. */
class NewsTestActions(val topOfHour: () -> Unit, val halfPast: () -> Unit)

/**
 * TEMPORARY, debug-only: read a real bulletin now, on demand, through the offline DJ
 * voice — fetches the live feeds, picks stories, builds the bulletin and speaks it. Null
 * in release builds. Standalone like [org.dylanjones.sleepradio.core.tts.rememberDebugTtsTest]
 * (own engine + player), so it works without a Broadcast running; it does NOT duck any
 * music that is playing. Remove once the real on-the-hour scheduling has shipped.
 */
@Composable
fun rememberDebugNewsTest(
    voiceSettings: suspend () -> NewsVoiceSettings,
    /** The master VOL knob (0..1) — Broadcast speech plays at master x announcer volume. */
    masterVolume: () -> Float,
    /** Announcer peak -> the VU meters, exactly as the Broadcast DJ player does. */
    onLevel: (Float) -> Unit,
): NewsTestActions? {
    if (!BuildConfig.DEBUG) return null

    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val engine = remember { OfflineTtsEngine() }
    val player = remember { DjVoicePlayer(engine) }
    player.onLevel = onLevel
    val masterNow by rememberUpdatedState(masterVolume)
    var announcerNow by remember { mutableStateOf(1f) }
    // Live, like the Broadcast DJ: the VOL knob moves the news reader mid-sentence too.
    player.volumeSource = { (masterNow() * announcerNow).coerceIn(0f, 1f) }
    val resolver = remember { VoicePackResolver(context) }
    val repo = remember { NewsRepository() }

    DisposableEffect(Unit) {
        onDispose {
            player.stop()
            engine.release()
        }
    }

    fun toast(msg: String, long: Boolean = false) = scope.launch(Dispatchers.Main) {
        Toast.makeText(context, msg, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    fun run(slot: NewsSlot) = scope.launch(Dispatchers.Default) {
        val vs = voiceSettings()
        val (djVoice, newsVoice) = vs.djVoice to vs.newsVoice
        val pack = resolveNewsPack(resolver, newsVoice, djVoice)
        if (pack == null) { toast("No voice installed — pick one in Broadcast voice", true); return@launch }
        Log.d("NewsDebug", "news reader voice: '${pack.id}' (news=$newsVoice, dj=$djVoice)")
        if (!engine.ensureLoaded(pack)) { toast("Voice engine failed to load", true); return@launch }
        toast("Fetching ${if (slot == NewsSlot.TOP_OF_HOUR) "top" else "soft"} stories…")
        val headlines = repo.headlinesFor(slot)
        val text = buildBulletin(slot, headlines, bulletinTimeLine(LocalTime.now()))
        if (text == null) { toast("No stories (offline, or nothing new to read)", true); return@launch }
        Log.d("NewsDebug", "bulletin ($slot): $text")
        withContext(Dispatchers.Main) { toast("Reading ${headlines.size} stories…") }
        // Same rule as real DJ speech (PlaybackConnection: masterGain * announcerVolume) — a
        // full-scale test would sound far louder than the DJ at a sleeping-level VOL setting.
        announcerNow = vs.announcerVolume
        val volume = (masterVolume() * vs.announcerVolume).coerceIn(0f, 1f)
        Log.d("NewsDebug", "speaking at volume $volume (master=${masterVolume()} announcer=${vs.announcerVolume})")
        player.speak(text, volume = volume)
        repo.markRead(headlines)
    }

    return NewsTestActions(
        topOfHour = { run(NewsSlot.TOP_OF_HOUR) },
        halfPast = { run(NewsSlot.HALF_PAST) },
    )
}
