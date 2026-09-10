package org.dylanjones.sleepradio.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dylanjones.sleepradio.core.audio.VuCalibrator
import org.dylanjones.sleepradio.core.data.VU_DELAY_MAX_MS
import kotlin.math.roundToInt

/**
 * Phase 16A/B — trim the VU meters' output-latency compensation. Auto picks a
 * delay per output route (phone speaker vs Bluetooth); Manual applies one value.
 * The mic auto-calibration (16B) plays a beep sequence, measures the delay and
 * saves it for the chosen route.
 */
@Composable
fun VuSyncDialog(
    auto: Boolean,
    phoneMs: Int,
    bluetoothMs: Int,
    customMs: Int,
    activeMs: Int,
    calState: VuCalibrator.Progress?,
    onAuto: (Boolean) -> Unit,
    onPhoneMs: (Int) -> Unit,
    onBluetoothMs: (Int) -> Unit,
    onCustomMs: (Int) -> Unit,
    onCalibrate: (bluetooth: Boolean) -> Unit,
    onCalDismiss: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onCalDismiss(); onDismiss() },
        confirmButton = {
            TextButton(onClick = { onCalDismiss(); onDismiss() }) { Text("Close") }
        },
        title = { Text("VU meter sync") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "The needles read ahead of the speaker by the audio output latency. " +
                        "Nudge these until they line up with a track that has sharp hits.",
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = auto, onClick = { onAuto(true) }, label = { Text("Auto") })
                    FilterChip(selected = !auto, onClick = { onAuto(false) }, label = { Text("Manual") })
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Applying now: $activeMs ms",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                if (auto) {
                    DelaySlider("Phone speaker", phoneMs, onPhoneMs)
                    DelaySlider("Bluetooth", bluetoothMs, onBluetoothMs)
                } else {
                    DelaySlider("Delay", customMs, onCustomMs)
                }

                HorizontalDivider(Modifier.padding(vertical = 14.dp))
                Text("CALIBRATE WITH MIC", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                CalibrationSection(calState, onCalibrate, onCalDismiss)
            }
        },
    )
}

@Composable
private fun CalibrationSection(
    state: VuCalibrator.Progress?,
    onCalibrate: (Boolean) -> Unit,
    onDone: () -> Unit,
) {
    when (state) {
        null -> {
            Text(
                "Plays a beep sequence and measures the delay by ear. Keep the room quiet; " +
                    "for Bluetooth, hold the phone within ~30 cm of the speaker.",
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onCalibrate(false) },
                    modifier = Modifier.weight(1f),
                ) { Text("Phone", maxLines = 1) }
                OutlinedButton(
                    onClick = { onCalibrate(true) },
                    modifier = Modifier.weight(1f),
                ) { Text("Bluetooth", maxLines = 1) }
            }
        }

        is VuCalibrator.Progress.Measuring -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(10.dp))
                Text(
                    if (state.readings == 0) "Listening…"
                    else "Measuring… ${state.estimateMs} ms  ±${state.spreadMs}  (${state.readings})",
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onDone) { Text("Stop") }
        }

        is VuCalibrator.Progress.Done -> {
            Text(
                "✓  Calibrated: ${state.delayMs} ms — saved",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onDone) { Text("OK") }
        }

        is VuCalibrator.Progress.Failed -> {
            Text(state.reason, fontSize = 12.sp)
            TextButton(onClick = onDone) { Text("Back") }
        }
    }
}

@Composable
private fun DelaySlider(label: String, ms: Int, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, fontSize = 13.sp)
            Text("$ms ms", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = ms.toFloat(),
            onValueChange = { onChange((it / 10f).roundToInt() * 10) },
            valueRange = 0f..VU_DELAY_MAX_MS.toFloat(),
            steps = VU_DELAY_MAX_MS / 10 - 1,
        )
    }
}
