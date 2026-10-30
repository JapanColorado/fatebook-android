package dev.russell.fatebook.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material3.TopAppBar
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.hilt.navigation.compose.hiltViewModel
import dev.russell.fatebook.ui.components.ReminderTimeDialog
import dev.russell.fatebook.ui.components.rememberNotificationPermissionRequester
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

    val requestNotificationPermission =
        rememberNotificationPermissionRequester(viewModel::onNotificationPermissionResult)

    LaunchedEffect(state.shouldRequestNotificationPermission) {
        if (state.shouldRequestNotificationPermission) requestNotificationPermission()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshExactAlarmAccess()
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
        onDailyReminderEnabledChanged = viewModel::setDailyReminderEnabled,
        onReadyToResolveEnabledChanged = viewModel::setReadyToResolveEnabled,
        onResolvingSoonEnabledChanged = viewModel::setResolvingSoonEnabled,
        onGetApiKeyClick = {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://fatebook.io/api-setup"))
            )
        },
        onAllowExactAlarmsClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:${context.packageName}"),
                    )
                )
            }
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
    onDailyReminderEnabledChanged: (Boolean) -> Unit = {},
    onReadyToResolveEnabledChanged: (Boolean) -> Unit = {},
    onResolvingSoonEnabledChanged: (Boolean) -> Unit = {},
    onGetApiKeyClick: () -> Unit = {},
    onAllowExactAlarmsClick: () -> Unit = {},
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

            SwitchRow(
                label = "Enable notifications",
                checked = state.notificationsEnabled,
                onCheckedChange = onNotificationsEnabledChanged,
            )

            if (state.notificationsEnabled) {
                var showTimePicker by remember { mutableStateOf(false) }

                TextButton(onClick = { showTimePicker = true }) {
                    Text(
                        "Notification time: %02d:%02d".format(state.reminderHour, state.reminderMinute)
                    )
                }

                if (!state.exactAlarmsAllowed) {
                    Text(
                        "Notifications may arrive late until you allow alarms & reminders.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onAllowExactAlarmsClick) {
                        Text("Allow alarms & reminders")
                    }
                }

                SwitchRow(
                    label = "Daily reminder to predict",
                    description = "Skipped on days you've already predicted",
                    checked = state.dailyReminderEnabled,
                    onCheckedChange = onDailyReminderEnabledChanged,
                )
                SwitchRow(
                    label = "Ready to resolve",
                    description = "One per overdue question, with YES / NO / +1 week",
                    checked = state.readyToResolveEnabled,
                    onCheckedChange = onReadyToResolveEnabledChanged,
                )
                SwitchRow(
                    label = "Resolving soon",
                    description = "A digest of questions due in the next 3 days",
                    checked = state.resolvingSoonEnabled,
                    onCheckedChange = onResolvingSoonEnabledChanged,
                )

                if (showTimePicker) {
                    ReminderTimeDialog(
                        hour = state.reminderHour,
                        minute = state.reminderMinute,
                        onConfirm = { hour, minute ->
                            onReminderTimeChanged(hour, minute)
                            showTimePicker = false
                        },
                        onDismiss = { showTimePicker = false },
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

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
