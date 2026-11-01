package dev.russell.fatebook.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.pow

// Paper + ink. Warm neutrals rather than Material's cool greys are most of what
// makes the app read as a printed ledger.
internal val PaperLight = Color(0xFFF6F2E9)
internal val PaperLightLow = Color(0xFFF1ECE1)
internal val PaperLightMid = Color(0xFFECE6D9)
internal val PaperLightHigh = Color(0xFFE6DFD0)
internal val PaperLightHighest = Color(0xFFE0D8C7)
internal val InkLight = Color(0xFF1D1B16)
internal val InkLightMuted = Color(0xFF5F594C)
internal val RuleLight = Color(0xFFD8D1C1)
internal val OutlineLight = Color(0xFF8C8574)

internal val PaperDark = Color(0xFF15140F)
internal val PaperDarkLow = Color(0xFF1C1B15)
internal val PaperDarkMid = Color(0xFF22201A)
internal val PaperDarkHigh = Color(0xFF2B2921)
internal val PaperDarkHighest = Color(0xFF353228)
internal val InkDark = Color(0xFFEDE7DA)
internal val InkDarkMuted = Color(0xFFA8A192)
internal val RuleDark = Color(0xFF35322A)
internal val OutlineDark = Color(0xFF6F695B)

/** Colours Material's scheme has no slot for. Read via [Ledger.colors]. */
@Immutable
data class LedgerColors(
    /** The "likely / resolved YES" end of the probability scale. */
    val yes: Color,
    /** The "unlikely / resolved NO" end of the probability scale. */
    val no: Color,
    /** 50% and ambiguous: deliberately carries no opinion. */
    val neutral: Color,
    /** The one attention colour: things that are due. Never encodes probability. */
    val accent: Color,
    val accentContainer: Color,
    /** Hairline between ledger rows. */
    val rule: Color,
)

internal val LightLedgerColors = LedgerColors(
    yes = Color(0xFF0B6E63),
    no = Color(0xFFB8452D),
    neutral = Color(0xFF6F6859),
    accent = Color(0xFF9A5B0C),
    accentContainer = Color(0xFFF4E2BF),
    rule = RuleLight,
)

internal val DarkLedgerColors = LedgerColors(
    yes = Color(0xFF5CC4B2),
    no = Color(0xFFF08A6F),
    neutral = Color(0xFFA39C8C),
    accent = Color(0xFFE3A84A),
    accentContainer = Color(0xFF3D2F16),
    rule = RuleDark,
)

val LocalLedgerColors = staticCompositionLocalOf { LightLedgerColors }

object Ledger {
    val colors: LedgerColors
        @Composable @ReadOnlyComposable
        get() = LocalLedgerColors.current
}

// Resolution colours deliberately reuse the ends of the probability scale, so
// "85% teal" and "resolved YES teal" read as the same side of the same question.
val ResolveYes: Color
    @Composable @ReadOnlyComposable
    get() = Ledger.colors.yes
val ResolveNo: Color
    @Composable @ReadOnlyComposable
    get() = Ledger.colors.no
val ResolveAmbiguous: Color
    @Composable @ReadOnlyComposable
    get() = Ledger.colors.neutral

// Multiple-choice option colours — earthy mid-tones that sit on paper and ink
// alike; cycled by option index in stacked option bars.
private val McOptionPalette = listOf(
    Color(0xFF3F5FA8), // ultramarine
    Color(0xFFC98A1B), // ochre
    Color(0xFF2E8B7A), // viridian
    Color(0xFF8A4F8F), // plum
    Color(0xFFC4603F), // terracotta
    Color(0xFF5B8FA8), // slate blue
    Color(0xFFB0577A), // rose madder
    Color(0xFF7D8A3A), // olive
)

fun mcOptionColor(index: Int): Color = McOptionPalette[index % McOptionPalette.size]

/**
 * Diverging probability scale, No ↔ neutral ↔ Yes. A 12% forecast isn't "bad" and
 * an 85% one isn't "good", so neither end is red-for-danger/green-for-success.
 */
fun probabilityColor(probability: Double, colors: LedgerColors): Color {
    val p = probability.coerceIn(0.0, 1.0).toFloat()
    // Eased so hue arrives quickly away from 50%: a straight lerp leaves everything
    // from 35% to 65% looking like the same muddy grey.
    val strength = abs(p - 0.5f) * 2f
    return lerp(colors.neutral, if (p >= 0.5f) colors.yes else colors.no, strength.pow(0.5f))
}

@Composable
@ReadOnlyComposable
fun forecastColor(probability: Double): Color = probabilityColor(probability, Ledger.colors)
