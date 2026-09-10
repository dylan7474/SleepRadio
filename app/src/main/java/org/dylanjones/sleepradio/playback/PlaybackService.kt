package org.dylanjones.sleepradio.playback

import android.content.Context
import android.content.Intent
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
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val mixer = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).mixer()

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

        // Sample the sink's per-channel peak off the audio thread and publish it
        // for the Studio skin's VU meters. Decay toward 0 so the needles fall
        // when playback stops (no buffers -> readLevels() returns 0).
        scope.launch {
            var l = 0f
            var r = 0f
            while (isActive) {
                val (pkL, pkR) = gainProcessor.readLevels()
                l = max(pkL, l * VU_DECAY)
                r = max(pkR, r * VU_DECAY)
                mixer.setVu(VuLevels(l, r))
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

    private companion object {
        const val VU_SAMPLE_MS = 40L
        /** Per-tick multiplier applied to the held level when no new peak arrives. */
        const val VU_DECAY = 0.80f
    }
}

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
