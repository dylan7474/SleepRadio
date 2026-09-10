package org.dylanjones.sleepradio.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dylanjones.sleepradio.core.data.VU_DELAY_MAX_MS
import kotlin.math.roundToInt

/**
 * Phase 16A — trim the VU meters' output-latency compensation. Auto picks a
 * delay per output route (phone speaker vs Bluetooth); Manual applies one value.
 * Phase 16B adds a "Calibrate with mic" button here.
 */
@Composable
fun VuSyncDialog(
    auto: Boolean,
    phoneMs: Int,
    bluetoothMs: Int,
    customMs: Int,
    activeMs: Int,
    onAuto: (Boolean) -> Unit,
    onPhoneMs: (Int) -> Unit,
    onBluetoothMs: (Int) -> Unit,
    onCustomMs: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
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
            }
        },
    )
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
