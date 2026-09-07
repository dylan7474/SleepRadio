package org.dylanjones.sleepradio.core.tts

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dylanjones.sleepradio.BuildConfig

/**
 * Debug-only smoke test for the TTS pipeline, before any real broadcast wiring
 * (Phase 9 Chunk A). Returns a click handler that loads the preferred voice
 * pack from `filesDir/tts/` and speaks a test line, or null in release builds.
 *
 * Push a voice pack first, e.g.:
 * ```
 * adb shell run-as org.dylanjones.sleepradio mkdir -p files/tts/stock
 * adb push model.onnx tokens.txt espeak-ng-data /sdcard/tts-stock/
 * # then move them into files/tts/stock/ via run-as
 * ```
 */
@Composable
fun rememberDebugTtsTest(): (() -> Unit)? {
    if (!BuildConfig.DEBUG) return null

    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val engine = remember { OfflineTtsEngine() }
    val player = remember { DjVoicePlayer(engine) }
    val resolver = remember { VoicePackResolver(context) }

    DisposableEffect(Unit) {
        onDispose {
            player.stop()
            engine.release()
        }
    }

    return {
        scope.launch(Dispatchers.Default) {
            val pack = resolver.preferred()
            if (pack == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "No voice in filesDir/tts — push one first",
                        Toast.LENGTH_LONG,
                    ).show()
                }
                return@launch
            }
            if (!engine.ensureLoaded(pack)) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Voice engine failed to load", Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Speaking via '${pack.id}'…", Toast.LENGTH_SHORT).show()
            }
            player.speak("Sleep Radio broadcast. This is a test of the offline voice.")
        }
    }
}
