package org.dylanjones.sleepradio.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.dylanjones.sleepradio.MainActivity
import org.dylanjones.sleepradio.core.audio.AmbientState
import org.dylanjones.sleepradio.core.audio.AudioChannel
import org.dylanjones.sleepradio.core.audio.BinauralGenerator
import org.dylanjones.sleepradio.core.audio.BinauralPreset
import org.dylanjones.sleepradio.core.audio.MixerController
import org.dylanjones.sleepradio.core.audio.MixerState
import org.dylanjones.sleepradio.core.audio.NoiseGenerator

/**
 * Channels B (noise) and C (binaural) run here, in their own foreground service,
 * so they keep playing when Channel A ([PlaybackService]) stops and when the app
 * is swiped from recents — the sleep-timer requirement (B/C continue after A
 * fades). Started by [PlaybackConnection] when either ambient channel turns on;
 * stops itself once both are off.
 *
 * Not `@AndroidEntryPoint` (keeps it clear of Media3's own service lifecycle);
 * pulls [MixerController] from Hilt via an [EntryPoint].
 */
class AmbientPlaybackService : Service() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun mixer(): MixerController
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val noise = NoiseGenerator()
    private val binaural = BinauralGenerator()
    private lateinit var mixer: MixerController
    private var observing: Job? = null
    private var startedForeground = false
    private var noisyRegistered = false

    /**
     * Headphones unplugged / BT disconnected — never blast ambient noise out of
     * the phone speaker (esp. at 3am). Channel A ([PlaybackService]) owns audio
     * focus; this layer deliberately doesn't fight it for focus, it just handles
     * the becoming-noisy case.
     */
    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) stopAmbient()
        }
    }

    override fun onCreate() {
        super.onCreate()
        mixer = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).mixer()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAmbient()
        }
        ensureForeground(label(mixer.ambient.value))
        registerNoisy()
        if (observing == null) startObserving()
        return START_STICKY
    }

    private fun startObserving() {
        observing = combine(mixer.state, mixer.ambient) { mix, amb -> mix to amb }
            .onEach { (mix, amb) ->
                applyState(mix, amb)
                if (!amb.noiseEnabled && amb.binaural == BinauralPreset.OFF) {
                    stopSelf()
                } else {
                    updateNotification(label(amb))
                }
            }
            .launchIn(scope)
    }

    private fun applyState(mix: MixerState, amb: AmbientState) {
        noise.setColor(amb.noiseColor)
        if (amb.noiseEnabled) {
            noise.setGain(mix.effectiveGain(AudioChannel.NOISE))
            noise.start()
        } else {
            noise.setGain(0f)
            noise.stop()
        }

        val bp = amb.binaural
        if (bp != BinauralPreset.OFF) {
            binaural.setTones(bp.carrierHz, bp.beatHz)
            binaural.setGain(mix.effectiveGain(AudioChannel.BINAURAL))
            binaural.start()
        } else {
            binaural.setGain(0f)
            binaural.stop()
        }
    }

    /** Clean stop: turn both channels off in the mixer; the observer stops the service. */
    private fun stopAmbient() {
        mixer.setNoiseEnabled(false)
        mixer.setBinaural(BinauralPreset.OFF)
    }

    private fun registerNoisy() {
        if (noisyRegistered) return
        ContextCompat.registerReceiver(
            this,
            becomingNoisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        noisyRegistered = true
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Keep playing when the task is swiped away — that is the whole point. */
    override fun onTaskRemoved(rootIntent: Intent?) = Unit

    override fun onDestroy() {
        observing?.cancel()
        noise.stop()
        binaural.stop()
        if (noisyRegistered) {
            runCatching { unregisterReceiver(becomingNoisyReceiver) }
            noisyRegistered = false
        }
        scope.cancel()
        super.onDestroy()
    }

    // --- notification ---

    private fun ensureForeground(text: String) {
        if (startedForeground) {
            updateNotification(text)
            return
        }
        val notif = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        startedForeground = true
    }

    private fun updateNotification(text: String) {
        if (!startedForeground) return
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, AmbientPlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ambient sound")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun createChannel() {
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Ambient sound",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { setShowBadge(false) },
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "ambient_playback"
        private const val NOTIF_ID = 42
        const val ACTION_STOP = "org.dylanjones.sleepradio.action.STOP_AMBIENT"

        fun start(context: Context) {
            val intent = Intent(context, AmbientPlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        private fun label(amb: AmbientState): String {
            val parts = buildList {
                if (amb.noiseEnabled) add(pretty(amb.noiseColor.name) + " noise")
                if (amb.binaural != BinauralPreset.OFF) add(pretty(amb.binaural.name) + " beats")
            }
            return parts.joinToString(" · ").ifEmpty { "Playing" }
        }

        private fun pretty(enumName: String): String =
            enumName.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}
