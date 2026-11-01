package dev.russell.fatebook.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.russell.fatebook.R

/** Questions and headings: they are prose, so they get a text face. */
val SerifFamily = FontFamily(
    Font(R.font.newsreader_medium, FontWeight.Medium),
    Font(R.font.newsreader_semibold, FontWeight.SemiBold),
    Font(R.font.newsreader_italic, FontWeight.Normal, FontStyle.Italic),
)

/** Numbers and data labels: monospaced digits keep columns of percentages aligned. */
val MonoFamily = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
    Font(R.font.plex_mono_semibold, FontWeight.SemiBold),
)

val SansFamily = FontFamily(
    Font(R.font.plex_sans_regular, FontWeight.Normal),
    Font(R.font.plex_sans_medium, FontWeight.Medium),
    Font(R.font.plex_sans_semibold, FontWeight.SemiBold),
)

val Typography = Typography(
    // Display: figures. The big percentage readouts and stat values.
    displayLarge = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 64.sp,
        lineHeight = 68.sp,
        letterSpacing = (-0.04).em,
    ),
    displayMedium = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.03).em,
    ),
    displaySmall = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.03).em,
    ),
    headlineLarge = TextStyle(
        fontFamily = SerifFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.01).em,
    ),
    headlineMedium = TextStyle(
        fontFamily = SerifFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = SerifFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = SerifFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = SerifFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    // Labels: the almanac voice. Pair with uppercase text.
    labelMedium = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.04.em,
    ),
    labelSmall = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.06.em,
    ),
)
