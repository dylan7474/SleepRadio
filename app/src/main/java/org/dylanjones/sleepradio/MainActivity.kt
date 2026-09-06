package org.dylanjones.sleepradio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import org.dylanjones.sleepradio.feature.player.PlayerRoute
import org.dylanjones.sleepradio.ui.theme.SleepRadioTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SleepRadioTheme {
                PlayerRoute()
            }
        }
    }
}
