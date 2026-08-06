package dev.russell.fatebook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.russell.fatebook.ui.theme.forecastColor
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProbabilitySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pct = (value * 100).roundToInt()
    val color = forecastColor(value.toDouble())

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledTonalIconButton(
                onClick = { onValueChange(BitsMath.applyBits(value, -1)) },
                modifier = Modifier
                    .size(36.dp)
                    .semantics { contentDescription = "Subtract one bit of evidence: halve the odds" },
            ) {
                Text("−", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = "$pct%",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 96.dp),
            )
            FilledTonalIconButton(
                onClick = { onValueChange(BitsMath.applyBits(value, 1)) },
                modifier = Modifier
                    .size(36.dp)
                    .semantics { contentDescription = "Add one bit of evidence: double the odds" },
            ) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0.01f..0.99f,
            steps = 97,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .semantics { contentDescription = "Probability: $pct percent" },
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
            ),
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            listOf(10, 25, 50, 75, 90).forEach { quickPct ->
                FilterChip(
                    selected = pct == quickPct,
                    onClick = { onValueChange(quickPct / 100f) },
                    label = { Text("$quickPct%") },
                )
            }
        }
    }
}
