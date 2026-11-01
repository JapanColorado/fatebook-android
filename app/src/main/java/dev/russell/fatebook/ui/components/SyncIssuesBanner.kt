package dev.russell.fatebook.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.russell.fatebook.ui.theme.Ledger

/**
 * Shown only when there are mutations that exhausted their retry budget. The
 * "View" button opens a sheet listing them with per-row Retry / Discard.
 */
@Composable
fun SyncIssuesBanner(
    count: Int,
    onView: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = MaterialTheme.colorScheme.onTertiaryContainer
    LedgerStrip(
        label = "Sync issues",
        message = if (count == 1) "1 change couldn't sync" else "$count changes couldn't sync",
        containerColor = Ledger.colors.accentContainer,
        contentColor = content,
        modifier = modifier,
    ) {
        LedgerAction(
            text = "View",
            onClick = onView,
            color = content,
            modifier = Modifier.padding(end = 12.dp),
        )
    }
}
