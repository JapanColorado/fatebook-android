package dev.russell.fatebook.ui.resolve

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.russell.fatebook.domain.model.Question
import dev.russell.fatebook.domain.model.Resolution
import dev.russell.fatebook.ui.theme.ResolveAmbiguous
import dev.russell.fatebook.ui.theme.ResolveNo
import dev.russell.fatebook.ui.theme.ResolveYes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResolveBottomSheet(
    question: Question,
    onResolve: (Resolution) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Resolve",
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = question.title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
            )

            question.forecastPercent?.let { pct ->
                Text(
                    text = "Your forecast: $pct%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { onResolve(Resolution.YES) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ResolveYes,
                    ),
                ) {
                    Text("YES", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { onResolve(Resolution.NO) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ResolveNo,
                    ),
                ) {
                    Text("NO", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { onResolve(Resolution.AMBIGUOUS) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Ambiguous")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
