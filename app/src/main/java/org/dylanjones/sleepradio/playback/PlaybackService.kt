package org.dylanjones.sleepradio.playback

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.dylanjones.sleepradio.core.audio.GainAudioProcessor
import org.dylanjones.sleepradio.core.audio.MixerController
import org.dylanjones.sleepradio.core.audio.VuLevels
import org.dylanjones.sleepradio.core.data.SettingsRepository
import kotlin.math.max

/**
 * Channel A (main audio) playback host. Runs as a [MediaSessionService] so
 * playback survives the app being backgrounded and shows a media notification /
 * lockscreen controls (Media3 provides the default notification).
 *
 * The ExoPlayer's audio sink carries one extra stage — a [GainAudioProcessor]
 * for Broadcast loudness levelling (Phase 12), fed a per-item gain via
 * [MixerController.itemGain]. It reports itself inactive for non-16-bit PCM and
 * is a straight passthrough at unity gain, so ordinary playback is unaffected.
 * [MixerController] is pulled from Hilt via an [EntryPoint], as in
 * [AmbientPlaybackService] (keeps clear of Media3's own service lifecycle).
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun mixer(): MixerController
        fun settings(): SettingsRepository
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val mixer = deps.mixer()
        val settings = deps.settings()
        val audioManager = getSystemService(AudioManager::class.java)

        // For http(s) streams, ask for Shoutcast/Icecast (ICY) in-stream
        // metadata so radio stations report the current "Artist - Track" —
        // Media3 merges IcyInfo.title into Player.mediaMetadata. Wrap it in a
        // DefaultDataSource.Factory so content:// / file:// URIs (local music,
        // audiobooks) still resolve to ContentDataSource / FileDataSource
        // instead of being forced through HTTP.
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("SleepRadio/1.0 (Android)")
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf("Icy-MetaData" to "1"))
        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)

        val gainProcessor = GainAudioProcessor()
        mixer.itemGain
            .onEach { gainProcessor.setGain(it) }
            .launchIn(scope)

        // VU sync config (Phase 16) — the sampler reads these each tick.
        var syncAuto = true
        var phoneMs = 120
        var btMs = 260
        var customMs = 150
        settings.vuSyncAuto.onEach { syncAuto = it }.launchIn(scope)
        settings.vuDelayPhoneMs.onEach { phoneMs = it }.launchIn(scope)
        settings.vuDelayBluetoothMs.onEach { btMs = it }.launchIn(scope)
        settings.vuDelayCustomMs.onEach { customMs = it }.launchIn(scope)

        // Sample the sink's per-channel peak off the audio thread and publish it
        // for the Studio skin's VU meters. Decay toward 0 so the needles fall
        // when playback stops (no buffers -> readLevels() returns 0). The
        // readings LEAD the speaker by the output latency, so publish through a
        // ring buffer delayed by the resolved VU-sync value.
        scope.launch {
            var l = 0f
            var r = 0f
            val bufL = FloatArray(VU_RING)
            val bufR = FloatArray(VU_RING)
            var head = 0
            var isBt = false
            var lastApplied = -1
            var tick = 0
            while (isActive) {
                if (tick % VU_ROUTE_POLL_TICKS == 0) isBt = hasBluetoothOutput(audioManager)
                tick++

                val (pkL, pkR) = gainProcessor.readLevels()
                val dj = mixer.takeDjPeak() // announcer's own AudioTrack, mono → both meters
                l = max(max(pkL, dj), l * VU_DECAY)
                r = max(max(pkR, dj), r * VU_DECAY)
                bufL[head] = l
                bufR[head] = r

                val delayMs = resolveVuDelayMs(syncAuto, isBt, phoneMs, btMs, customMs)
                if (delayMs != lastApplied) {
                    mixer.setActiveVuDelayMs(delayMs)
                    lastApplied = delayMs
                }
                val back = (delayMs / VU_SAMPLE_MS.toInt()).coerceIn(0, VU_RING - 1)
                val idx = (head - back + VU_RING) % VU_RING
                mixer.setVu(VuLevels(bufL[idx], bufR[idx]))

                head = (head + 1) % VU_RING
                delay(VU_SAMPLE_MS)
            }
        }

        val player = ExoPlayer.Builder(this)
            .setRenderersFactory(NormalisingRenderersFactory(this, gainProcessor))
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun hasBluetoothOutput(am: AudioManager?): Boolean {
        val devices = am?.getDevices(AudioManager.GET_DEVICES_OUTPUTS) ?: return false
        return devices.any { d ->
            d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                d.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                d.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    d.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
        }
    }

    private companion object {
        const val VU_SAMPLE_MS = 33L
        /** Per-tick multiplier applied to the held level when no new peak arrives
         *  (≈ 0.78 → −10 % over ~40 ms; a lively return swing, not a slow VU crawl). */
        const val VU_DECAY = 0.78f
        /** Ring long enough for the max VU-sync delay plus headroom. */
        const val VU_RING = 24
        /** Re-check the output route (BT vs speaker) this many sampler ticks apart (~1 s). */
        const val VU_ROUTE_POLL_TICKS = 30
    }
}

/**
 * Which VU-meter delay (ms) applies: the per-route value when [auto], else the
 * single [custom] value. Pure — unit-tested.
 */
internal fun resolveVuDelayMs(
    auto: Boolean,
    isBluetooth: Boolean,
    phoneMs: Int,
    bluetoothMs: Int,
    customMs: Int,
): Int = if (!auto) customMs else if (isBluetooth) bluetoothMs else phoneMs

/**
 * [DefaultRenderersFactory] that splices [gain] into the audio sink's processor
 * chain — the supported way to add a wideband gain stage that can also *boost*
 * (Player.volume is capped at 1.0, so it can only attenuate).
 */
@UnstableApi
private class NormalisingRenderersFactory(
    context: Context,
    private val gain: GainAudioProcessor,
) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink = DefaultAudioSink.Builder(context)
        .setEnableFloatOutput(enableFloatOutput)
        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
        .setAudioProcessors(arrayOf(gain))
        .build()
}
