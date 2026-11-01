package dev.russell.fatebook.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.russell.fatebook.ui.components.LedgerAction
import dev.russell.fatebook.ui.components.LedgerMargin
import dev.russell.fatebook.ui.components.LedgerRule
import dev.russell.fatebook.ui.components.LedgerSection
import dev.russell.fatebook.ui.components.MastheadRule
import dev.russell.fatebook.ui.components.ReminderTimeDialog
import dev.russell.fatebook.ui.components.SectionGap
import dev.russell.fatebook.ui.components.SectionLabel
import dev.russell.fatebook.ui.components.rememberNotificationPermissionRequester
import dev.russell.fatebook.ui.theme.Ledger

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
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SettingsMasthead(onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = LedgerMargin)
                    .padding(top = 8.dp, bottom = 24.dp),
            ) {
                // No rule of its own: the masthead's sits directly above.
                SectionLabel("API key", modifier = Modifier.padding(bottom = 12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.apiKey,
                        onValueChange = onApiKeyChanged,
                        label = { Text("Fatebook API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )

                    Button(
                        onClick = onValidateAndSave,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = MaterialTheme.shapes.medium,
                        enabled = state.apiKey.isNotBlank() && !state.isValidating,
                    ) {
                        if (state.isValidating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Save & Connect")
                        }
                    }

                    when (state.validationResult) {
                        true -> Text(
                            "Connected successfully!",
                            color = Ledger.colors.yes,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        false -> Text(
                            state.validationError ?: "Validation failed",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        null -> {}
                    }

                    LedgerAction(
                        text = "Get a key from fatebook.io",
                        onClick = onGetApiKeyClick,
                    )
                }

                SectionGap()

                LedgerSection(title = "Notifications") {
                    Column {
                        SwitchRow(
                            label = "Enable notifications",
                            checked = state.notificationsEnabled,
                            onCheckedChange = onNotificationsEnabledChanged,
                        )

                        if (state.notificationsEnabled) {
                            var showTimePicker by remember { mutableStateOf(false) }

                            LedgerRule()
                            TimeRow(
                                hour = state.reminderHour,
                                minute = state.reminderMinute,
                                onClick = { showTimePicker = true },
                            )

                            if (!state.exactAlarmsAllowed) {
                                ExactAlarmNotice(onAllowExactAlarmsClick)
                            }

                            LedgerRule()
                            SwitchRow(
                                label = "Daily reminder to predict",
                                description = "Skipped on days you've already predicted",
                                checked = state.dailyReminderEnabled,
                                onCheckedChange = onDailyReminderEnabledChanged,
                            )
                            LedgerRule()
                            SwitchRow(
                                label = "Ready to resolve",
                                description = "One per overdue question, with YES / NO / +1 week",
                                checked = state.readyToResolveEnabled,
                                onCheckedChange = onReadyToResolveEnabledChanged,
                            )
                            LedgerRule()
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
                    }
                }

                SectionGap()

                LedgerSection(title = "Privacy") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Your API key is stored locally using encrypted storage. " +
                                "Predictions are cached on-device for offline access. " +
                                "No data is shared with third parties. No analytics or tracking.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        LedgerAction(text = "Privacy Policy", onClick = onPrivacyPolicyClick)
                    }
                }
            }
        }
    }
}

/** Same front-page treatment as the feed, so the two tabs read as one publication. */
@Composable
private fun SettingsMasthead(onBack: (() -> Unit)?) {
    Column(modifier = Modifier.padding(horizontal = LedgerMargin)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() },
            )
        }
        MastheadRule(Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun TimeRow(
    hour: Int,
    minute: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Notification time", style = MaterialTheme.typography.bodyLarge)
        Text(
            "%02d:%02d".format(hour, minute),
            style = MaterialTheme.typography.displaySmall,
        )
    }
}

@Composable
private fun ExactAlarmNotice(onAllow: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ledger.colors.accentContainer)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Notifications may arrive late until you allow alarms & reminders.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        LedgerAction(
            text = "Allow alarms & reminders",
            onClick = onAllow,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
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
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // The whole row toggles; a null callback keeps the switch from taking a second focus stop.
        Switch(checked = checked, onCheckedChange = null)
    }
}
