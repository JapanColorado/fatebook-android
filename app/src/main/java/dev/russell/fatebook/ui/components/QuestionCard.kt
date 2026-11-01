package dev.russell.fatebook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.russell.fatebook.domain.model.Question
import dev.russell.fatebook.domain.model.QuestionType
import dev.russell.fatebook.domain.model.Resolution
import dev.russell.fatebook.ui.theme.Ledger
import dev.russell.fatebook.ui.theme.ResolveAmbiguous
import dev.russell.fatebook.ui.theme.ResolveNo
import dev.russell.fatebook.ui.theme.ResolveYes
import dev.russell.fatebook.ui.theme.forecastColor
import dev.russell.fatebook.ui.theme.mcOptionColor
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * One entry in the ledger: serif question, mono figure, probability bar, and a
 * line of metadata. Deliberately has no card chrome. The list's hairlines do the
 * grouping, which lets the numbers carry the visual weight. [footer] is a slot
 * for row-level actions (the Due tab's resolve strip).
 */
@Composable
fun QuestionCard(
    question: Question,
    onClick: (Question) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    footer: (@Composable () -> Unit)? = null,
) {
    // rememberUpdatedState so the click reads the latest question after edits —
    // keying on question.id alone leaves a stale closure when fields change.
    val currentQuestion by rememberUpdatedState(question)
    val rowClick = remember(onClick) { { onClick(currentQuestion) } }
    // Clock reads (Instant.now / LocalDate.now) and the allocating resolveByDate
    // getter are per-question facts for display purposes — compute once per
    // question instead of on every recomposition.
    val dateLine = remember(question, today) {
        if (question.resolved) {
            "Resolved ${question.resolvedOnDate.format(MONTH_DAY_YEAR)}"
        } else {
            relativeDate(question.resolveByDate, today)
        }.uppercase(Locale.US)
    }
    val urgency = remember(question, today) { urgencyOf(question, today) }
    val forecastHidden = remember(question) { question.isForecastHidden }
    val forecastAge = remember(question) {
        question.latestForecastAt?.takeUnless { forecastHidden }
            ?.let { "Updated ${timeAgo(it)}".uppercase(Locale.US) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = rowClick)
            .padding(horizontal = LedgerMargin, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = question.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Figure(question, forecastHidden)
        }

        QuestionBar(question, forecastHidden)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (urgency == Urgency.OVERDUE) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = Ledger.colors.accent,
                )
            }
            Text(
                text = dateLine,
                style = MaterialTheme.typography.labelSmall,
                color = if (urgency == Urgency.NONE) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    Ledger.colors.accent
                },
                maxLines = 1,
            )
            if (question.tags.isNotEmpty()) {
                TagChipRow(tags = question.tags, maxVisible = 2)
            }
            Spacer(Modifier.weight(1f))
            if (forecastAge != null) {
                Text(
                    text = forecastAge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        footer?.invoke()
    }
}

/** The right-hand figure: a percentage, or a stamp once the question has resolved. */
@Composable
private fun Figure(question: Question, forecastHidden: Boolean) {
    when {
        question.resolved -> ResolvedFigure(question)
        question.type == QuestionType.QUANTITY -> FigureCaption("Quantity")
        forecastHidden -> FigureCaption("Hidden")
        question.type == QuestionType.MULTIPLE_CHOICE -> {
            val top = question.options
                .filter { it.latestForecast != null }
                .maxByOrNull { it.latestForecast!! }
            if (top?.forecastPercent != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Percent(top.forecastPercent!!, forecastColor(top.latestForecast!!))
                    Text(
                        text = top.text,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 104.dp),
                    )
                }
            } else {
                FigureCaption("${question.options.size} options")
            }
        }
        else -> question.forecastPercent?.let { Percent(it, forecastColor(it / 100.0)) }
            ?: FigureCaption("No forecast")
    }
}

@Composable
private fun ResolvedFigure(question: Question) {
    if (question.type == QuestionType.MULTIPLE_CHOICE) {
        val winner = question.winningOption
        when {
            winner != null -> OutcomeStamp(winner.text, ResolveYes)
            question.resolution == Resolution.AMBIGUOUS -> OutcomeStamp("N/A", ResolveAmbiguous)
            else -> OutcomeStamp("Other", ResolveNo)
        }
    } else {
        when (question.resolution) {
            Resolution.YES -> OutcomeStamp("Yes", ResolveYes)
            Resolution.NO -> OutcomeStamp("No", ResolveNo)
            else -> OutcomeStamp("N/A", ResolveAmbiguous)
        }
    }
}

@Composable
private fun Percent(pct: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.clearAndSetSemantics { contentDescription = "$pct percent" },
    ) {
        Text(
            text = "$pct",
            style = MaterialTheme.typography.displaySmall,
            color = color,
        )
        Text(
            text = "%",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(start = 1.dp, top = 3.dp),
        )
    }
}

@Composable
private fun FigureCaption(text: String) {
    Text(
        text = text.uppercase(Locale.US),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 5.dp),
    )
}

@Composable
private fun QuestionBar(question: Question, forecastHidden: Boolean) {
    if (forecastHidden || question.type == QuestionType.QUANTITY) return
    val fractions: List<Pair<Float, Color>> = if (question.type == QuestionType.MULTIPLE_CHOICE) {
        val forecast = question.options.filter { it.latestForecast != null }
        if (forecast.isEmpty()) return
        question.options.mapIndexedNotNull { i, option ->
            option.latestForecast?.let { it.toFloat() to mcOptionColor(i) }
        }
    } else {
        val p = question.yourLatestForecast ?: return
        listOf(p.toFloat() to forecastColor(p))
    }
    // Once resolved the bar is history, not the headline: the stamp is.
    ProbabilityBar(
        fractions = fractions,
        modifier = Modifier.alpha(if (question.resolved) 0.45f else 1f),
    )
}

/** Compact tag labels; shows up to [maxVisible] tags plus a "+n" overflow. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagChipRow(
    tags: List<String>,
    modifier: Modifier = Modifier,
    maxVisible: Int = Int.MAX_VALUE,
) {
    // A printed "#tag", not a chip: these rows are dense enough without pills.
    // FlowRow with maxLines drops whichever tags don't fit rather than squeezing the row.
    FlowRow(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        maxLines = 1,
    ) {
        tags.take(maxVisible).forEach { tag ->
            TagLabel("#$tag")
        }
        val overflow = tags.size - maxVisible
        if (overflow > 0) {
            TagLabel("+$overflow")
        }
    }
}

@Composable
private fun TagLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 96.dp),
    )
}

private val MONTH_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val MONTH_DAY_YEAR = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

private fun timeAgo(instant: Instant): String {
    val ago = Duration.between(instant, Instant.now())
    return when {
        ago.toDays() > 30 -> instant.atZone(ZoneId.systemDefault()).format(MONTH_DAY)
        ago.toDays() > 0 -> "${ago.toDays()}d ago"
        ago.toHours() > 0 -> "${ago.toHours()}h ago"
        else -> "just now"
    }
}

private enum class Urgency { NONE, DUE_TODAY, OVERDUE }

private fun urgencyOf(question: Question, today: LocalDate): Urgency = when {
    question.resolved -> Urgency.NONE
    question.resolveByDate.isBefore(today) -> Urgency.OVERDUE
    question.resolveByDate == today -> Urgency.DUE_TODAY
    else -> Urgency.NONE
}

private fun relativeDate(date: LocalDate, today: LocalDate): String {
    val daysDiff = ChronoUnit.DAYS.between(today, date)
    return when {
        daysDiff < 0 -> "${-daysDiff}d overdue"
        daysDiff == 0L -> "due today"
        daysDiff == 1L -> "tomorrow"
        daysDiff < 7 -> "in ${daysDiff}d"
        daysDiff < 30 -> "in ${daysDiff / 7}w"
        // Beyond a year out, "Jun 1" alone is ambiguous — say which year.
        date.isAfter(today.plusYears(1)) -> date.format(MONTH_DAY_YEAR)
        else -> date.format(MONTH_DAY)
    }
}
