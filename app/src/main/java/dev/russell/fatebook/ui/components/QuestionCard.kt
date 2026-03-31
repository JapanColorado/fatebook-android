package dev.russell.fatebook.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.russell.fatebook.domain.model.Question
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                    text = if (question.resolved) {
                        "Resolved ${absoluteDate(question.resolveByDate)}"
                    } else {
                        "Resolution ${relativeDate(question.resolveByDate)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(start = 12.dp),
            ) {
                if (question.resolved && question.resolution != null) {
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
                } else if (question.isForecastHidden) {
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
                if (!question.isForecastHidden) {
                    question.latestForecastAt?.let { forecastAt ->
                        Text(
                            text = "Predicted ${timeAgo(forecastAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun timeAgo(instant: Instant): String {
    val ago = Duration.between(instant, Instant.now())
    return when {
        ago.toDays() > 30 -> instant.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d"))
        ago.toDays() > 0 -> "${ago.toDays()}d ago"
        ago.toHours() > 0 -> "${ago.toHours()}h ago"
        else -> "just now"
    }
}

private fun absoluteDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))

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
        else -> date.format(DateTimeFormatter.ofPattern("MMM d"))
    }
}
