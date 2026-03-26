package dev.russell.fatebook.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.russell.fatebook.domain.model.Question
import dev.russell.fatebook.ui.theme.ResolveAmbiguous
import dev.russell.fatebook.ui.theme.ResolveNo
import dev.russell.fatebook.ui.theme.ResolveYes
import dev.russell.fatebook.ui.theme.forecastColor
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionDetailSheet(
    question: Question,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            // Title
            Text(
                text = question.title,
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Resolve-by date
            Text(
                text = "Resolves: ${
                    question.resolveBy
                        .atZone(ZoneId.systemDefault())
                        .format(dateFormatter)
                }",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Forecast
            question.forecastPercent?.let { pct ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Your forecast: $pct%",
                    color = forecastColor(pct / 100.0),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }

            // Resolution (if resolved)
            if (question.resolved && question.resolution != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val (label, color) = when (question.resolution.apiValue) {
                    "YES" -> "Resolved: YES" to ResolveYes
                    "NO" -> "Resolved: NO" to ResolveNo
                    else -> "Resolved: Ambiguous" to ResolveAmbiguous
                }
                Text(
                    text = label,
                    color = color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Open in browser
            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(question.url)))
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Open in Fatebook")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
