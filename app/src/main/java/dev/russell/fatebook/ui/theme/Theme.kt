package dev.russell.fatebook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Ink is the primary colour: primary actions are solid ink on paper. The ochre
// accent is reserved for "this is due" (see LedgerColors.accent).
private val LightColorScheme = lightColorScheme(
    primary = InkLight,
    onPrimary = PaperLight,
    primaryContainer = PaperLightHigh,
    onPrimaryContainer = InkLight,
    secondary = InkLightMuted,
    onSecondary = PaperLight,
    secondaryContainer = PaperLightMid,
    onSecondaryContainer = InkLight,
    tertiary = LightLedgerColors.accent,
    onTertiary = PaperLight,
    tertiaryContainer = LightLedgerColors.accentContainer,
    onTertiaryContainer = InkLight,
    background = PaperLight,
    onBackground = InkLight,
    surface = PaperLight,
    onSurface = InkLight,
    surfaceVariant = PaperLightMid,
    onSurfaceVariant = InkLightMuted,
    surfaceTint = Color.Transparent,
    surfaceBright = PaperLight,
    surfaceDim = PaperLightHigh,
    surfaceContainerLowest = PaperLight,
    surfaceContainerLow = PaperLightLow,
    surfaceContainer = PaperLightMid,
    surfaceContainerHigh = PaperLightHigh,
    surfaceContainerHighest = PaperLightHighest,
    outline = OutlineLight,
    outlineVariant = RuleLight,
    error = Color(0xFFA8321F),
    onError = PaperLight,
    errorContainer = Color(0xFFF6DDD5),
    onErrorContainer = Color(0xFF4F150A),
)

private val DarkColorScheme = darkColorScheme(
    primary = InkDark,
    onPrimary = PaperDark,
    primaryContainer = PaperDarkHigh,
    onPrimaryContainer = InkDark,
    secondary = InkDarkMuted,
    onSecondary = PaperDark,
    secondaryContainer = PaperDarkHigh,
    onSecondaryContainer = InkDark,
    tertiary = DarkLedgerColors.accent,
    onTertiary = PaperDark,
    tertiaryContainer = DarkLedgerColors.accentContainer,
    onTertiaryContainer = InkDark,
    background = PaperDark,
    onBackground = InkDark,
    surface = PaperDark,
    onSurface = InkDark,
    surfaceVariant = PaperDarkMid,
    onSurfaceVariant = InkDarkMuted,
    surfaceTint = Color.Transparent,
    surfaceBright = PaperDarkHighest,
    surfaceDim = PaperDark,
    surfaceContainerLowest = PaperDark,
    surfaceContainerLow = PaperDarkLow,
    surfaceContainer = PaperDarkMid,
    surfaceContainerHigh = PaperDarkHigh,
    surfaceContainerHighest = PaperDarkHighest,
    outline = OutlineDark,
    outlineVariant = RuleDark,
    error = Color(0xFFF2937A),
    onError = PaperDark,
    errorContainer = Color(0xFF4A1E13),
    onErrorContainer = Color(0xFFF9DAD1),
)

private val LedgerShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(10.dp),
)

@Composable
fun FatebookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalLedgerColors provides if (darkTheme) DarkLedgerColors else LightLedgerColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            shapes = LedgerShapes,
            content = content,
        )
    }
}
