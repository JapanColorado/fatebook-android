package dev.russell.fatebook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** A notice inset into the page: flat, full width, closed with a hairline. */
@Composable
internal fun LedgerStrip(
    label: String,
    message: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(containerColor)
                .padding(start = LedgerMargin, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            ) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                )
            }
            trailing()
        }
        LedgerRule()
    }
}

@Composable
fun ErrorBanner(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String = "Retry",
    onAction: (() -> Unit)? = null,
) {
    val content = MaterialTheme.colorScheme.onErrorContainer
    LedgerStrip(
        label = "Error",
        message = message,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = content,
        modifier = modifier,
    ) {
        LedgerAction(text = actionLabel, onClick = onAction ?: onRetry, color = content)
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss",
                tint = content,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
