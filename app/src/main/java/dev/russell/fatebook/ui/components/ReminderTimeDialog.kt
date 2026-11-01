package dev.russell.fatebook.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable

/** Clock picker for the daily notification time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderTimeDialog(
    hour: Int,
    minute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val timePickerState = rememberTimePickerState(initialHour = hour, initialMinute = minute)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        confirmButton = {
            Button(
                onClick = { onConfirm(timePickerState.hour, timePickerState.minute) },
                shape = MaterialTheme.shapes.medium,
            ) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Notification time") },
        text = { TimePicker(state = timePickerState) },
    )
}
