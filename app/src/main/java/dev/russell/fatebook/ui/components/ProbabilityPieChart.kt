package dev.russell.fatebook.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.russell.fatebook.ui.theme.mcOptionColor
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** One slice of the probability pie. [value] is a fraction in [0, 1]. */
data class PieSegment(
    val id: String,
    val label: String,
    val value: Float,
)

/**
 * Interactive pie chart for exclusive multiple-choice forecasts. Slice
 * fractions always sum to 1 — dragging the handle on a boundary between two
 * slices transfers probability between them and leaves the rest untouched.
 * The boundary at 12 o'clock is fixed.
 */
@Composable
fun ProbabilityPieChart(
    segments: List<PieSegment>,
    onValuesChange: (List<Float>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (segments.size < 2) return

    val currentSegments by rememberUpdatedState(segments)
    val currentOnValuesChange by rememberUpdatedState(onValuesChange)
    var activeBoundary by remember { mutableStateOf<Int?>(null) }

    val textMeasurer = rememberTextMeasurer()
    val handleRadiusPx = with(LocalDensity.current) { 9.dp.toPx() }
    val surfaceColor = MaterialTheme.colorScheme.surface
    val handleBorderColor = MaterialTheme.colorScheme.onSurface

    val percents = PieChartMath.displayPercents(segments.map { it.value })
    val summary = segments.mapIndexed { i, s -> "${s.label} ${percents[i]}%" }
        .joinToString(", ")

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.15f)
            .semantics { contentDescription = "Forecast pie chart: $summary" }
            .pointerInput(segments.size, enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        val values = currentSegments.map { it.value }
                        activeBoundary = grabbedBoundary(
                            position = offset,
                            canvasSize = Size(size.width.toFloat(), size.height.toFloat()),
                            values = values,
                        )
                    },
                    onDragEnd = { activeBoundary = null },
                    onDragCancel = { activeBoundary = null },
                ) { change, _ ->
                    change.consume()
                    val boundary = activeBoundary ?: return@detectDragGestures
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val fraction = fractionAt(change.position, center) ?: return@detectDragGestures
                    // Snap to whole percents so the displayed values are stable.
                    val snapped = (fraction * 100).roundToInt() / 100f
                    currentOnValuesChange(
                        PieChartMath.moveBoundary(currentSegments.map { it.value }, boundary, snapped),
                    )
                }
            },
    ) {
        val values = segments.map { it.value }
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f - handleRadiusPx
        val arcRect = Rect(center - Offset(radius, radius), Size(radius * 2, radius * 2))
        val cumulative = PieChartMath.cumulative(values)

        values.forEachIndexed { i, value ->
            drawArc(
                color = mcOptionColor(i),
                startAngle = START_ANGLE + cumulative[i] * 360f,
                sweepAngle = value * 360f,
                useCenter = true,
                topLeft = arcRect.topLeft,
                size = arcRect.size,
            )
        }

        // Thin gaps between slices, in the surface color.
        for (i in values.indices) {
            val angle = Math.toRadians((START_ANGLE + cumulative[i] * 360f).toDouble())
            drawLine(
                color = surfaceColor,
                start = center,
                end = center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * radius,
                strokeWidth = 3.dp.toPx(),
            )
        }

        // Percent labels inside slices big enough to hold them.
        values.forEachIndexed { i, value ->
            if (value < 0.055f) return@forEachIndexed
            val midAngle = Math.toRadians(
                (START_ANGLE + (cumulative[i] + value / 2f) * 360f).toDouble(),
            )
            val labelPos = center +
                Offset(cos(midAngle).toFloat(), sin(midAngle).toFloat()) * (radius * 0.62f)
            val layout = textMeasurer.measure(
                text = "${percents[i]}%",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            drawText(
                textLayoutResult = layout,
                topLeft = labelPos - Offset(layout.size.width / 2f, layout.size.height / 2f),
            )
        }

        // Drag handles on every movable boundary (all but the fixed one at 12
        // o'clock).
        for (boundary in 1 until values.size) {
            val angle = Math.toRadians((START_ANGLE + cumulative[boundary] * 360f).toDouble())
            val handleCenter = center +
                Offset(cos(angle).toFloat(), sin(angle).toFloat()) * radius
            val active = activeBoundary == boundary
            drawCircle(
                color = surfaceColor,
                radius = if (active) handleRadiusPx * 1.2f else handleRadiusPx,
                center = handleCenter,
            )
            drawCircle(
                color = if (enabled) handleBorderColor else handleBorderColor.copy(alpha = 0.4f),
                radius = (if (active) handleRadiusPx * 1.2f else handleRadiusPx) * 0.55f,
                center = handleCenter,
            )
        }
    }
}

/** Pie starts at 12 o'clock and runs clockwise. */
private const val START_ANGLE = -90f

/** How far (as a fraction of the circle) a touch can be from a boundary and still grab it. */
private const val GRAB_TOLERANCE = 0.09f

/** Cumulative fraction of the circle at [position], or null too close to the center. */
private fun fractionAt(position: Offset, center: Offset): Float? {
    val d = position - center
    if (d.getDistance() < 24f) return null
    val degrees = Math.toDegrees(atan2(d.y.toDouble(), d.x.toDouble())).toFloat()
    return ((degrees - START_ANGLE + 360f) % 360f) / 360f
}

private fun grabbedBoundary(position: Offset, canvasSize: Size, values: List<Float>): Int? {
    val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
    val fraction = fractionAt(position, center) ?: return null
    val cumulative = PieChartMath.cumulative(values)
    var best: Int? = null
    var bestDistance = GRAB_TOLERANCE
    for (boundary in 1 until values.size) {
        val raw = abs(fraction - cumulative[boundary])
        val distance = min(raw, 1f - raw)
        if (distance <= bestDistance) {
            best = boundary
            bestDistance = distance
        }
    }
    return best
}
