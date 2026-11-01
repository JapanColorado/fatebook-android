package dev.russell.fatebook.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.russell.fatebook.ui.theme.Ledger
import dev.russell.fatebook.ui.theme.forecastColor
import kotlin.math.roundToInt

private val QUICK_SET = listOf(10, 25, 50, 75, 90)
private const val MIN = 0.01f
private const val MAX = 0.99f

/**
 * The forecast control: a large readout with a plain-language gloss, ±1 bit of
 * evidence buttons, and a ruler you drag along. Quick-set values sit on the ruler
 * at their true positions rather than in a separate row of chips, so tapping 75
 * lands the needle right above the label.
 */
@Composable
fun ProbabilitySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pct = (value * 100).roundToInt()
    val color by animateColorAsState(forecastColor(value.toDouble()), label = "forecastColor")

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BitButton("−1", "Subtract one bit of evidence: halve the odds") {
                onValueChange(BitsMath.applyBits(value, -1))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$pct%",
                    style = MaterialTheme.typography.displayLarge,
                    color = color,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = probabilityWords(value).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BitButton("+1", "Add one bit of evidence: double the odds") {
                onValueChange(BitsMath.applyBits(value, 1))
            }
        }

        Ruler(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** ±1 bit: a square key rather than a tonal pill, labelled with its unit. */
@Composable
private fun BitButton(label: String, description: String, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .size(56.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .clickable(role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = MaterialTheme.typography.labelMedium.fontFamily),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "BIT",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Ruler(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val currentValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onValueChange)
    val colors = Ledger.colors
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    val needleColor = MaterialTheme.colorScheme.onSurface
    val pct = (value * 100).roundToInt()

    // Whole-percent steps, with a light tick each time a 5% mark is crossed.
    fun set(fraction: Float) {
        val snapped = ((fraction * 100).roundToInt() / 100f).coerceIn(MIN, MAX)
        val previous = (currentValue * 100).roundToInt()
        val next = (snapped * 100).roundToInt()
        if (next != previous) {
            if (next / 5 != previous / 5) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            currentOnChange(snapped)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .semantics {
                    contentDescription = "Probability: $pct percent"
                    stateDescription = "$pct percent"
                    progressBarRangeInfo = ProgressBarRangeInfo(value, MIN..MAX, steps = 97)
                    setProgress { target ->
                        currentOnChange(target.coerceIn(MIN, MAX))
                        true
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { set(it.x / size.width) }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        set(change.position.x / size.width)
                    }
                },
        ) {
            val base = size.height - 6.dp.toPx()
            val gradientHeight = 3.dp.toPx()
            drawRect(
                brush = Brush.horizontalGradient(listOf(colors.no, colors.neutral, colors.yes)),
                topLeft = Offset(0f, base),
                size = Size(size.width, gradientHeight),
            )
            for (i in 0..100 step 5) {
                val x = size.width * i / 100f
                val major = i % 25 == 0
                val tick = if (major) 14.dp.toPx() else 7.dp.toPx()
                drawLine(
                    color = tickColor.copy(alpha = if (major) 1f else 0.55f),
                    start = Offset(x.coerceIn(0.5f, size.width - 0.5f), base),
                    end = Offset(x.coerceIn(0.5f, size.width - 0.5f), base - tick),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            val x = size.width * value
            drawRect(
                color = needleColor,
                topLeft = Offset(x - 1.5.dp.toPx(), 2.dp.toPx()),
                size = Size(3.dp.toPx(), size.height - 2.dp.toPx()),
            )
        }

        // Quick-set figures, placed under their ticks.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
        ) {
            val slot = 44.dp
            QUICK_SET.forEach { quick ->
                val selected = pct == quick
                Box(
                    modifier = Modifier
                        .offset(x = maxWidth * (quick / 100f) - slot / 2)
                        .size(slot, 36.dp)
                        .clickable(role = Role.Button) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onValueChange(quick / 100f)
                        }
                        .clearAndSetSemantics { contentDescription = "Set to $quick percent" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$quick",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

/** Plain-language gloss, so the number has a felt meaning while you drag. */
internal fun probabilityWords(probability: Float): String = when {
    probability >= 0.95f -> "Near certain"
    probability >= 0.80f -> "Very likely"
    probability >= 0.60f -> "Likely"
    probability > 0.40f -> "Toss-up"
    probability > 0.20f -> "Unlikely"
    probability > 0.05f -> "Very unlikely"
    else -> "Near impossible"
}
