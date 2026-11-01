package dev.russell.fatebook.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.russell.fatebook.ui.theme.Ledger
import dev.russell.fatebook.ui.theme.forecastColor

/**
 * A small step chart of one user's forecasts over a question's lifetime:
 * 0–100% vertically, created→now (or resolved) horizontally, with a hairline
 * at 50%. The end dot takes the latest forecast's colour on the No↔Yes scale.
 */
@Composable
fun ForecastSparkline(
    points: List<SparklinePoint>,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val line = MaterialTheme.colorScheme.onSurface
    val reference = Ledger.colors.rule
    val endColor = points.lastOrNull()?.let { forecastColor(it.y.toDouble()) } ?: line
    val polyline = remember(points) { SparklineMath.stepPolyline(points) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        val w = size.width
        val h = size.height
        val pad = 4.dp.toPx()
        fun xFor(x: Float) = pad + x * (w - 2 * pad)
        fun yFor(y: Float) = pad + (1f - y) * (h - 2 * pad)

        drawLine(
            color = reference,
            start = Offset(xFor(0f), yFor(0.5f)),
            end = Offset(xFor(1f), yFor(0.5f)),
            strokeWidth = 1.dp.toPx(),
        )
        for (i in 1 until polyline.size) {
            drawLine(
                color = line,
                start = Offset(xFor(polyline[i - 1].x), yFor(polyline[i - 1].y)),
                end = Offset(xFor(polyline[i].x), yFor(polyline[i].y)),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        points.lastOrNull()?.let { last ->
            drawCircle(endColor, radius = 4.dp.toPx(), center = Offset(xFor(last.x), yFor(last.y)))
        }
    }
}
