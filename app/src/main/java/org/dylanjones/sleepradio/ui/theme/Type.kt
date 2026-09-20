package org.dylanjones.sleepradio.ui.theme

import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The phone's built-in condensed face: the lettering of a radio's engraved plates and labels. */
private val Condensed = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))

// Reading text (body styles) stays in the default face; titles and labels take the condensed plate look.
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    titleLarge = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = 1.sp),
    titleMedium = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = 0.8.sp),
    titleSmall = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.8.sp),
    labelLarge = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = 1.2.sp),
    labelMedium = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 1.2.sp),
    labelSmall = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 1.5.sp),
)
