package org.dylanjones.sleepradio.debug

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.dylanjones.sleepradio.core.ai.DjCommentaryEngine
import org.json.JSONArray

/**
 * DEBUG ONLY. Same process/foreground-service situation as the real Broadcast (a mediaPlayback
 * foreground service, screen off): can AICore still serve Gemini Nano? Logs "FGS_RESULT" lines.
 *
 * Start: `adb shell am start-foreground-service -n org.dylanjones.sleepradio/.debug.AiLabService`
 * then turn the screen off (`adb shell input keyevent KEYCODE_SLEEP`).
 */
class AiLabService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("ailab", "AI lab", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "ailab").setContentTitle("AI lab running")
            .setSmallIcon(android.R.drawable.ic_media_play).build()
        startForeground(42, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sleepradio:ailabsvc").apply { acquire(10 * 60_000L) }
        CoroutineScope(Dispatchers.Default).launch {
            try {
                delay(20_000) // time to turn the screen off
                val arr = JSONArray(java.io.File(getExternalFilesDir(null), "news_lab.json").readText())
                val engine = DjCommentaryEngine()
                Log.d("AiLab", "FGS_RESULT status=${engine.status()} screenOn=${pm.isInteractive}")
                for (i in 0 until 6) {
                    val o = arr.getJSONObject(i)
                    val out = withTimeoutOrNull(40_000) {
                        engine.generateLink("Rewrite the headline as one calm spoken sentence. Use only the given facts.", o.getString("title"), 0.4f, 20, 100)
                    }
                    Log.d("AiLab", "FGS_RESULT #$i screenOn=${pm.isInteractive} out=$out")
                    delay(4_000)
                }
                Log.d("AiLab", "FGS_RESULT DONE")
            } finally {
                if (wl.isHeld) wl.release()
                stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
            }
        }
        return START_NOT_STICKY
    }
}
