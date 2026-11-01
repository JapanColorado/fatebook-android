package dev.russell.fatebook.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Persistent banner shown while the device has no validated internet connection.
 * Mutations still work — they apply to local Room and sit in the sync queue
 * until connectivity returns.
 */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    LedgerStrip(
        label = "Offline",
        message = "You're offline. Changes will sync when you reconnect.",
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
