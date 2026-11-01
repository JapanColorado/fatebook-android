package dev.russell.fatebook.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.russell.fatebook.ui.feed.SyncErrorEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncErrorsSheet(
    errors: List<SyncErrorEntry>,
    onRetryAll: () -> Unit,
    onDiscard: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LedgerMargin, vertical = 8.dp),
        ) {
            Text(
                text = "Sync issues",
                style = MaterialTheme.typography.headlineMedium,
            )
            MastheadRule(Modifier.padding(top = 4.dp, bottom = 12.dp))
            Text(
                text = "These changes are stored locally but couldn't reach the server.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            LedgerRule()

            // fill = false: a long list scrolls without pushing Retry off the sheet.
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(errors, key = { it.id }) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp),
                        ) {
                            Text(
                                text = entry.type.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                text = entry.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        LedgerAction(text = "Discard", onClick = { onDiscard(entry.id) })
                    }
                    LedgerRule()
                }
            }

            Button(
                onClick = onRetryAll,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp)
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text("Retry all")
            }
        }
    }
}
