package org.dylanjones.sleepradio.feature.player

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

private const val BAR_COUNT = 32

/**
 * The tick strip under the now-playing block. When audio is playing and the user
 * has granted `RECORD_AUDIO`, it renders live FFT bars from the system output
 * mix (Channel A + noise + binaural). Otherwise it falls back to a gently
 * animated decorative pattern. Permission is requested once, on first playback.
 */
@Composable
fun AudioVisualizerStrip(
    playing: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = it }

    LaunchedEffect(playing) {
        if (playing && !granted && !asked) {
            asked = true
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Live FFT bars (0..1). Non-empty only while a Visualizer is running.
    var bars by remember { mutableStateOf(FloatArray(0)) }

    DisposableEffect(playing, granted) {
        var viz: Visualizer? = null
        if (playing && granted) {
            viz = runCatching {
                Visualizer(0).apply {
                    captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
                    val smooth = FloatArray(BAR_COUNT)
                    setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(v: Visualizer?, data: ByteArray?, rate: Int) = Unit
                            override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, rate: Int) {
                                if (fft == null) return
                                val raw = fftToBars(fft)
                                for (i in smooth.indices) {
                                    // fast attack, slow decay — VU-meter feel
                                    smooth[i] = maxOf(raw[i], smooth[i] * 0.82f)
                                }
                                bars = smooth.copyOf()
                            }
                        },
                        Visualizer.getMaxCaptureRate() / 2,
                        false,
                        true,
                    )
                    enabled = true
                }
            }.getOrNull()
        }
        onDispose {
            runCatching {
                viz?.enabled = false
                viz?.release()
            }
            bars = FloatArray(0)
        }
    }

    val idle by rememberInfiniteTransition(label = "viz-idle").animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "viz-idle-phase",
    )

    Canvas(modifier) {
        val live = bars
        val hasLive = live.isNotEmpty() && live.any { it > 0.02f }
        val n = BAR_COUNT
        val slot = size.width / n
        val barW = slot * 0.55f
        for (i in 0 until n) {
            val level = if (hasLive) {
                live[i]
            } else {
                // Decorative: a slow travelling wave, taller in the middle.
                val envelope = 0.35f + 0.65f * sin((i.toFloat() / n) * Math.PI.toFloat())
                (0.18f + 0.30f * abs(sin(i * 0.5f + idle))) * envelope
            }
            val h = (size.height * level).coerceIn(1.5f, size.height)
            val x = i * slot + (slot - barW) / 2f
            drawRoundRect(
                color = if (hasLive || i % 4 == 0) color else color.copy(alpha = 0.35f),
                topLeft = Offset(x, (size.height - h) / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f),
            )
        }
    }
}

/** Convert a [Visualizer] FFT byte buffer into [BAR_COUNT] normalised magnitudes. */
private fun fftToBars(fft: ByteArray): FloatArray {
    val out = FloatArray(BAR_COUNT)
    val bins = fft.size / 2
    if (bins < 2) return out
    // Ignore the top octave (mostly hiss); spread the rest across the bars.
    val usable = (bins * 3) / 4
    for (i in 0 until BAR_COUNT) {
        val lo = (usable * i / BAR_COUNT).coerceAtLeast(1)
        val hi = (usable * (i + 1) / BAR_COUNT).coerceIn(lo + 1, bins)
        var mag = 0f
        for (k in lo until hi) {
            val re = fft[2 * k].toFloat()
            val im = fft[2 * k + 1].toFloat()
            mag += sqrt(re * re + im * im)
        }
        mag /= (hi - lo)
        // Log compression; factor tuned for a lively-but-not-clipped strip.
        out[i] = (ln(1f + mag) / 3.6f).coerceIn(0f, 1f)
    }
    return out
}
