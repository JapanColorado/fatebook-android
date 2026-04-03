package dev.russell.fatebook.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: (() -> Unit)?,
    onValidated: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onNotificationPermissionResult(granted)
    }

    LaunchedEffect(state.shouldRequestNotificationPermission) {
        if (state.shouldRequestNotificationPermission) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    viewModel.onNotificationPermissionResult(true)
                } else {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                // Pre-Android 13 — permission not needed at runtime
                viewModel.onNotificationPermissionResult(true)
            }
        }
    }

    LaunchedEffect(state.validationResult) {
        if (state.validationResult == true) {
            onValidated?.invoke()
        }
    }

    SettingsScreenContent(
        state = state,
        onBack = onBack,
        onApiKeyChanged = viewModel::setApiKey,
        onValidateAndSave = viewModel::validateAndSave,
        onNotificationsEnabledChanged = viewModel::setNotificationsEnabled,
        onReminderTimeChanged = viewModel::setReminderTime,
        onGetApiKeyClick = {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://fatebook.io/api-setup"))
            )
        },
        onPrivacyPolicyClick = {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://japancolorado.github.io/FatebookApp/privacy-policy"))
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    state: SettingsUiState,
    onBack: (() -> Unit)? = null,
    onApiKeyChanged: (String) -> Unit = {},
    onValidateAndSave: () -> Unit = {},
    onNotificationsEnabledChanged: (Boolean) -> Unit = {},
    onReminderTimeChanged: (Int, Int) -> Unit = { _, _ -> },
    onGetApiKeyClick: () -> Unit = {},
    onPrivacyPolicyClick: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // --- API Key ---
            Text("API Key", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = state.apiKey,
                onValueChange = onApiKeyChanged,
                label = { Text("Fatebook API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )

            TextButton(onClick = onGetApiKeyClick) {
                Text("Get your API key from fatebook.io")
            }

            Button(
                onClick = onValidateAndSave,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.apiKey.isNotBlank() && !state.isValidating,
            ) {
                if (state.isValidating) {
                    CircularProgressIndicator()
                } else {
                    Text("Save & Connect")
                }
            }

            when (state.validationResult) {
                true -> Text(
                    "Connected successfully!",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
                false -> Text(
                    state.validationError ?: "Validation failed",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                null -> {}
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Notifications ---
            Text("Notifications", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Daily reminder")
                Switch(
                    checked = state.notificationsEnabled,
                    onCheckedChange = onNotificationsEnabledChanged,
                )
            }

            if (state.notificationsEnabled) {
                var showTimePicker by remember { mutableStateOf(false) }

                TextButton(onClick = { showTimePicker = true }) {
                    Text(
                        "Reminder time: %02d:%02d".format(state.reminderHour, state.reminderMinute)
                    )
                }

                if (showTimePicker) {
                    val timePickerState = rememberTimePickerState(
                        initialHour = state.reminderHour,
                        initialMinute = state.reminderMinute,
                    )
                    AlertDialog(
                        onDismissRequest = { showTimePicker = false },
                        confirmButton = {
                            Button(onClick = {
                                onReminderTimeChanged(
                                    timePickerState.hour,
                                    timePickerState.minute,
                                )
                                showTimePicker = false
                            }) {
                                Text("Set")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showTimePicker = false }) {
                                Text("Cancel")
                            }
                        },
                        title = { Text("Reminder time") },
                        text = { TimePicker(state = timePickerState) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Privacy ---
            HorizontalDivider()

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Your API key is stored locally using encrypted storage. " +
                    "Predictions are cached on-device for offline access. " +
                    "No data is shared with third parties. No analytics or tracking.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TextButton(onClick = onPrivacyPolicyClick) {
                Text("Privacy Policy")
            }
        }
    }
}
