package dev.russell.fatebook.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.russell.fatebook.ui.components.LedgerRule

enum class LedgerTab(val route: String, val label: String, val icon: ImageVector) {
    PREDICTIONS(Routes.FEED, "Ledger", Icons.AutoMirrored.Outlined.MenuBook),
    RECORD(Routes.ANALYTICS, "Record", Icons.Outlined.Insights),
    SETTINGS(Routes.SETTINGS, "Settings", Icons.Outlined.Settings),
}

/**
 * Three destinations plus the app's primary verb. New Prediction lives in the bar
 * rather than a FAB so it's in the same place on every tab and never covers a row.
 */
@Composable
fun LedgerBottomBar(
    selected: LedgerTab?,
    onSelect: (LedgerTab) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding()) {
            LedgerRule()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(start = 8.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .selectableGroup(),
                ) {
                    LedgerTab.entries.forEach { tab ->
                        TabItem(
                            tab = tab,
                            selected = tab == selected,
                            onClick = { onSelect(tab) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.onSurface, MaterialTheme.shapes.medium)
                        .clickable(role = Role.Button, onClickLabel = "New prediction", onClick = onCreate),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "New prediction",
                        tint = MaterialTheme.colorScheme.surface,
                    )
                }
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: LedgerTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(
            text = tab.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(
            Modifier
                .padding(top = 2.dp)
                .height(2.dp)
                .width(20.dp)
                .background(if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent),
        )
    }
}
