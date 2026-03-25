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
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
            verticalAlignment = Alignment.Top,
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
                    text = relativeDate(question.resolveBy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

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
                    modifier = Modifier.padding(start = 12.dp),
                )
            } else {
                question.forecastPercent?.let { pct ->
                    Text(
                        text = "$pct%",
                        color = forecastColor(pct / 100.0),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
}

private fun relativeDate(instant: Instant): String {
    val now = Instant.now()
    val duration = Duration.between(now, instant)
    return when {
        duration.isNegative -> {
            val ago = duration.abs()
            when {
                ago.toDays() > 0 -> "${ago.toDays()}d overdue"
                ago.toHours() > 0 -> "${ago.toHours()}h overdue"
                else -> "just now"
            }
        }
        duration.toDays() == 0L -> "today"
        duration.toDays() == 1L -> "tomorrow"
        duration.toDays() < 7 -> "in ${duration.toDays()}d"
        duration.toDays() < 30 -> "in ${duration.toDays() / 7}w"
        else -> instant.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d"))
    }
}
