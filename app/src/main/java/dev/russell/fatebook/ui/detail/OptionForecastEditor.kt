package dev.russell.fatebook.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.russell.fatebook.domain.model.QuestionOption
import dev.russell.fatebook.ui.components.BitButton
import dev.russell.fatebook.ui.components.LedgerAction
import dev.russell.fatebook.ui.components.LedgerRule
import dev.russell.fatebook.ui.components.OptionForecastMath
import dev.russell.fatebook.ui.components.ProbabilityRuler
import dev.russell.fatebook.ui.theme.forecastColor
import kotlin.math.roundToInt

/**
 * Ruled rows for forecasting a multiple-choice question: each option has its own
 * ruler and ±1 bit keys, and moving one never disturbs another. An exclusive
 * question closes with a tally, since its options share 100% and whatever they
 * leave is the question's Other outcome.
 */
@Composable
internal fun OptionForecastEditor(
    options: List<QuestionOption>,
    drafts: Map<String, Int>,
    exclusive: Boolean,
    onDraftChange: (optionId: String, percent: Int) -> Unit,
    onBalance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { i, option ->
            if (i > 0) LedgerRule()
            OptionForecastRow(
                option = option,
                draft = drafts[option.id],
                onDraftChange = { percent -> onDraftChange(option.id, percent) },
            )
        }
        if (exclusive) {
            OptionTally(drafts = drafts, onBalance = onBalance)
        }
    }
}

@Composable
private fun OptionForecastRow(
    option: QuestionOption,
    draft: Int?,
    onDraftChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = option.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (option.resolution != null) {
                OptionOutcome(option.resolution)
            } else {
                val previous = option.forecastPercent
                if (previous != null && previous != draft) {
                    Text(
                        text = "WAS $previous",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = draft?.let { "$it%" } ?: "—",
                    style = monoFigure(),
                    color = draft?.let { forecastColor(it / 100.0) }
                        ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (option.resolution == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Bits scale existing odds, so they wait until the option has some.
                BitButton(
                    label = "−1",
                    description = "${option.text}: subtract one bit of evidence",
                    compact = true,
                    enabled = draft != null,
                    onClick = { draft?.let { onDraftChange(OptionForecastMath.applyBits(it, -1)) } },
                )
                ProbabilityRuler(
                    value = draft?.let { it / 100f },
                    onValueChange = { onDraftChange((it * 100).roundToInt()) },
                    label = option.text,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                )
                BitButton(
                    label = "+1",
                    description = "${option.text}: add one bit of evidence",
                    compact = true,
                    enabled = draft != null,
                    onClick = { draft?.let { onDraftChange(OptionForecastMath.applyBits(it, 1)) } },
                )
            }
        }
    }
}

/** The foot of the column: a heavy rule, what's left for Other, and the total. */
@Composable
private fun OptionTally(
    drafts: Map<String, Int>,
    onBalance: () -> Unit,
) {
    val overBy = OptionForecastMath.overBy(drafts)
    Column {
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onSurface)
        if (overBy == 0) {
            TallyLine(
                label = "Left for Other",
                figure = "${OptionForecastMath.leftForOther(drafts)}%",
                muted = true,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        TallyLine(
            label = "Total",
            figure = "${100 + overBy}%",
            modifier = Modifier.padding(top = if (overBy == 0) 4.dp else 10.dp),
        )
        if (overBy > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "OVER BY $overBy",
                    style = MaterialTheme.typography.labelMedium,
                )
                LedgerAction(text = "Balance", onClick = onBalance)
            }
        }
    }
}

@Composable
private fun TallyLine(
    label: String,
    figure: String,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
) {
    val color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = color)
        Text(figure, style = monoFigure(), color = color)
    }
}
