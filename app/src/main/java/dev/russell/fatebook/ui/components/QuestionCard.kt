package dev.russell.fatebook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.russell.fatebook.domain.model.Question
import dev.russell.fatebook.domain.model.QuestionType
import dev.russell.fatebook.ui.theme.ResolveAmbiguous
import dev.russell.fatebook.ui.theme.ResolveNo
import dev.russell.fatebook.ui.theme.ResolveYes
import dev.russell.fatebook.ui.theme.forecastColor
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun QuestionCard(
    question: Question,
    onClick: (Question) -> Unit,
    modifier: Modifier = Modifier,
) {
    // rememberUpdatedState so the click reads the latest question after edits —
    // keying on question.id alone leaves a stale closure when fields change.
    val currentQuestion by rememberUpdatedState(question)
    val cardClick = remember(onClick) { { onClick(currentQuestion) } }
    // Clock reads (Instant.now / LocalDate.now) and the allocating resolveByDate
    // getter are per-question facts for display purposes — compute once per
    // question instead of on every recomposition.
    val dateLine = remember(question) {
        if (question.resolved) {
            "Resolved ${absoluteDate(question.resolveByDate)}"
        } else {
            "Resolution ${relativeDate(question.resolveByDate)}"
        }
    }
    val forecastHidden = remember(question) { question.isForecastHidden }
    val predictedLine = remember(question) {
        question.latestForecastAt?.let { "Predicted ${timeAgo(it)}" }
    }
    Card(
        onClick = cardClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = question.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = dateLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (question.tags.isNotEmpty()) {
                    TagChipRow(tags = question.tags, maxVisible = 2)
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(start = 12.dp),
            ) {
                if (question.type == QuestionType.QUANTITY) {
                    Text(
                        text = "Quantity",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                } else if (question.type == QuestionType.MULTIPLE_CHOICE) {
                    MultipleChoiceSummary(question)
                } else if (question.resolved && question.resolution != null) {
                    val (text, color) = when (question.resolution.apiValue) {
                        "YES" -> "YES" to ResolveYes
                        "NO" -> "NO" to ResolveNo
                        else -> "N/A" to ResolveAmbiguous
                    }
                    Text(
                        text = text,
                        color = color,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                } else if (forecastHidden) {
                    Text(
                        text = "Hidden",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                } else {
                    question.forecastPercent?.let { pct ->
                        Text(
                            text = "$pct%",
                            color = forecastColor(pct / 100.0),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        )
                    }
                }
                if (!forecastHidden && predictedLine != null) {
                    Text(
                        text = predictedLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Compact tag chips; shows up to [maxVisible] tags plus a "+n" overflow chip. */
@Composable
fun TagChipRow(
    tags: List<String>,
    modifier: Modifier = Modifier,
    maxVisible: Int = Int.MAX_VALUE,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tags.take(maxVisible).forEach { tag ->
            TagChip(tag)
        }
        val overflow = tags.size - maxVisible
        if (overflow > 0) {
            TagChip("+$overflow")
        }
    }
}

@Composable
private fun TagChip(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 120.dp)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/**
 * Right-column summary for a multiple-choice card: the winning option when
 * resolved, otherwise the option currently forecast highest.
 */
@Composable
private fun MultipleChoiceSummary(question: Question) {
    if (question.resolved) {
        val winner = question.winningOption
        Text(
            text = winner?.text ?: "N/A",
            color = if (winner != null) ResolveYes else ResolveAmbiguous,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp),
        )
    } else {
        val top = question.options
            .filter { it.latestForecast != null }
            .maxByOrNull { it.latestForecast!! }
        if (top?.forecastPercent != null) {
            Text(
                text = "${top.forecastPercent}%",
                color = forecastColor(top.latestForecast!!),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
            Text(
                text = top.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp),
            )
        } else {
            Text(
                text = "${question.options.size} options",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            )
        }
    }
}

private val MONTH_DAY = DateTimeFormatter.ofPattern("MMM d")
private val MONTH_DAY_YEAR = DateTimeFormatter.ofPattern("MMM d, yyyy")

private fun timeAgo(instant: Instant): String {
    val ago = Duration.between(instant, Instant.now())
    return when {
        ago.toDays() > 30 -> instant.atZone(ZoneId.systemDefault()).format(MONTH_DAY)
        ago.toDays() > 0 -> "${ago.toDays()}d ago"
        ago.toHours() > 0 -> "${ago.toHours()}h ago"
        else -> "just now"
    }
}

private fun absoluteDate(date: LocalDate): String = date.format(MONTH_DAY_YEAR)

private fun relativeDate(date: LocalDate): String {
    val today = LocalDate.now()
    val daysDiff = ChronoUnit.DAYS.between(today, date)
    return when {
        daysDiff < -1 -> "${-daysDiff}d overdue"
        daysDiff == -1L -> "1d overdue"
        daysDiff == 0L -> "today"
        daysDiff == 1L -> "tomorrow"
        daysDiff < 7 -> "in ${daysDiff}d"
        daysDiff < 30 -> "in ${daysDiff / 7}w"
        // Beyond a year out, "Jun 1" alone is ambiguous — say which year.
        date.isAfter(today.plusYears(1)) -> date.format(MONTH_DAY_YEAR)
        else -> date.format(MONTH_DAY)
    }
}
