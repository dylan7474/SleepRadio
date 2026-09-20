package org.dylanjones.sleepradio.core.design

import androidx.compose.ui.graphics.Color

/**
 * The app's skin: a warm hi-fi console. Amber-on-espresso with cream tiles, one control
 * row (RWD · VOL · PAUSE · BAL · FFWD), half-height SLEEP/NOISE tiles and a pair of
 * analogue L/R VU meters.
 */
object StudioSkin : AppSkin {
    override val colors = SkinColors(
        screenTop = Color(0xFF15120E),
        screenBottom = Color(0xFF221C14),
        panelTop = Color(0xFF3A322A),
        panelBottom = Color(0xFF241E18),
        panelStroke = Color(0xFF4E443A),
        accent = Color(0xFFE0A64C),
        accentAlt = Color(0xFFC24B3A),
        glow = Color(0xFFE0A64C),
        tileFace = Color(0xFF2C2620),
        tileFaceActive = Color(0xFF473A2B),
        tileStroke = Color(0xFF5C5044),
        onTile = Color(0xFFEDE3D3),
        textPrimary = Color(0xFFF2E9D9),
        textSecondary = Color(0xFFB9AC97),
        textDim = Color(0xFF7C7060),
    )
}
