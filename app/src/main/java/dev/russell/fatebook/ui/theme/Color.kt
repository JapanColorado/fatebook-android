package dev.russell.fatebook.ui.theme

import androidx.compose.ui.graphics.Color

// Primary palette
val Blue40 = Color(0xFF1A73E8)
val Blue80 = Color(0xFF8AB4F8)
val BlueGrey10 = Color(0xFF1A1C1E)
val BlueGrey90 = Color(0xFFE2E2E6)
val BlueGrey95 = Color(0xFFF0F0F4)

// Forecast confidence colors
val ForecastHigh = Color(0xFF34A853)   // ≥ 80% — strong confidence
val ForecastMedHigh = Color(0xFF8BC34A) // 60-79%
val ForecastMedium = Color(0xFFFBBC04)  // 40-59%
val ForecastMedLow = Color(0xFFFF9800)  // 20-39%
val ForecastLow = Color(0xFFEA4335)     // < 20% — low confidence

// Resolution colors
val ResolveYes = Color(0xFF34A853)
val ResolveNo = Color(0xFFEA4335)
val ResolveAmbiguous = Color(0xFF9E9E9E)

// Multiple-choice option colors — mid-tone hues that read on both light and
// dark surfaces; cycled by option index in the pie chart and option rows.
private val McOptionPalette = listOf(
    Color(0xFF4285F4), // blue
    Color(0xFFF9AB00), // amber
    Color(0xFF34A853), // green
    Color(0xFF9334E6), // purple
    Color(0xFFFA7B17), // orange
    Color(0xFF12B5CB), // teal
    Color(0xFFF538A0), // pink
    Color(0xFFEA4335), // red
)

fun mcOptionColor(index: Int): Color = McOptionPalette[index % McOptionPalette.size]

fun forecastColor(probability: Double): Color = when {
    probability >= 0.80 -> ForecastHigh
    probability >= 0.60 -> ForecastMedHigh
    probability >= 0.40 -> ForecastMedium
    probability >= 0.20 -> ForecastMedLow
    else -> ForecastLow
}
