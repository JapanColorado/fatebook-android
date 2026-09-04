package dev.russell.fatebook.ui.onboarding

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.russell.fatebook.ui.components.ReminderTimeDialog
import dev.russell.fatebook.ui.components.rememberNotificationPermissionRequester

private const val API_SETUP_URL = "https://fatebook.io/api-setup"

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val requestNotificationPermission =
        rememberNotificationPermissionRequester(viewModel::onNotificationPermissionResult)
    LaunchedEffect(state.shouldRequestNotificationPermission) {
        if (state.shouldRequestNotificationPermission) requestNotificationPermission()
    }
    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }
    BackHandler(enabled = state.page > 0) { viewModel.back() }

    OnboardingScreenContent(
        state = state,
        onNext = viewModel::next,
        onBack = viewModel::back,
        onApiKeyChanged = viewModel::setApiKey,
        onConnect = viewModel::connect,
        onOpenApiSetup = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(API_SETUP_URL)))
        },
        onNotificationsEnabledChanged = viewModel::setNotificationsEnabled,
        onReminderTimeChanged = viewModel::setReminderTime,
        onFinish = viewModel::finish,
    )
}

@Composable
fun OnboardingScreenContent(
    state: OnboardingUiState,
    onNext: () -> Unit = {},
    onBack: () -> Unit = {},
    onApiKeyChanged: (String) -> Unit = {},
    onConnect: () -> Unit = {},
    onOpenApiSetup: () -> Unit = {},
    onNotificationsEnabledChanged: (Boolean) -> Unit = {},
    onReminderTimeChanged: (Int, Int) -> Unit = { _, _ -> },
    onFinish: () -> Unit = {},
) {
    val pagerState = rememberPagerState(initialPage = state.page) { ONBOARDING_PAGE_COUNT }
    LaunchedEffect(state.page) {
        if (pagerState.currentPage != state.page) pagerState.animateScrollToPage(state.page)
    }

    Scaffold(
        bottomBar = {
            OnboardingBottomBar(
                state = state,
                onNext = onNext,
                onBack = onBack,
                onConnect = onConnect,
                onFinish = onFinish,
            )
        },
    ) { padding ->
        // The pager is driven by state.page; swiping is off so a user can't
        // skip past the API key page.
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (page) {
                    OnboardingViewModel.PAGE_WELCOME -> WelcomePage()
                    OnboardingViewModel.PAGE_API_KEY -> ApiKeyPage(
                        state = state,
                        onApiKeyChanged = onApiKeyChanged,
                        onOpenApiSetup = onOpenApiSetup,
                    )
                    OnboardingViewModel.PAGE_NOTIFICATIONS -> NotificationsPage(
                        state = state,
                        onNotificationsEnabledChanged = onNotificationsEnabledChanged,
                        onReminderTimeChanged = onReminderTimeChanged,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingBottomBar(
    state: OnboardingUiState,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onConnect: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PageDots(count = ONBOARDING_PAGE_COUNT, current = state.page)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.page > 0) {
                TextButton(onClick = onBack) { Text("Back") }
            } else {
                Spacer(Modifier.width(1.dp))
            }
            when (state.page) {
                OnboardingViewModel.PAGE_WELCOME -> Button(onClick = onNext) { Text("Get started") }
                OnboardingViewModel.PAGE_API_KEY -> if (state.connected) {
                    Button(onClick = onNext) { Text("Next") }
                } else {
                    Button(
                        onClick = onConnect,
                        enabled = state.apiKey.isNotBlank() && !state.isConnecting,
                    ) {
                        if (state.isConnecting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Connect")
                        }
                    }
                }
                else -> Button(onClick = onFinish) {
                    Text(if (state.notificationsEnabled) "Finish" else "Skip for now")
                }
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (index == current) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun WelcomePage() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(72.dp),
    )
    Spacer(Modifier.height(24.dp))
    Text(
        text = "Welcome to Fatebook",
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = "Write down what you think will happen, say how sure you are, and find out how well-calibrated you really are.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(32.dp))
    FeatureRow(
        icon = Icons.Default.Add,
        title = "Predict",
        body = "Jot down a question, a resolve-by date and a probability in seconds — even offline.",
    )
    Spacer(Modifier.height(16.dp))
    FeatureRow(
        icon = Icons.Default.CheckCircle,
        title = "Resolve",
        body = "When the date comes, mark it YES or NO, straight from a notification if you like.",
    )
    Spacer(Modifier.height(16.dp))
    FeatureRow(
        icon = Icons.Default.BarChart,
        title = "Calibrate",
        body = "See your Brier score, calibration curve and streak grow over time.",
    )
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    body: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ApiKeyPage(
    state: OnboardingUiState,
    onApiKeyChanged: (String) -> Unit,
    onOpenApiSetup: () -> Unit,
) {
    Text(
        text = "Connect your account",
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = "The app talks to fatebook.io with a personal API key. Create one on the website, then paste it below. It's stored encrypted on this device only.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    OutlinedButton(onClick = onOpenApiSetup, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Open fatebook.io/api-setup")
    }
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = state.apiKey,
        onValueChange = onApiKeyChanged,
        label = { Text("Fatebook API key") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !state.isConnecting && !state.connected,
        isError = state.connectError != null,
        visualTransformation = PasswordVisualTransformation(),
        supportingText = {
            when {
                state.connectError != null -> Text(
                    state.connectError,
                    color = MaterialTheme.colorScheme.error,
                )
                state.connected -> Text(
                    "Connected!",
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

@Composable
private fun NotificationsPage(
    state: OnboardingUiState,
    onNotificationsEnabledChanged: (Boolean) -> Unit,
    onReminderTimeChanged: (Int, Int) -> Unit,
) {
    var showTimePicker by remember { mutableStateOf(false) }

    Text(
        text = "A daily nudge",
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = "Once a day, Fatebook can remind you to make a prediction and tell you which questions are ready to resolve. Each kind can be tuned in Settings later.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(32.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Enable notifications", style = MaterialTheme.typography.titleMedium)
        Switch(
            checked = state.notificationsEnabled,
            onCheckedChange = onNotificationsEnabledChanged,
        )
    }
    if (state.notificationsEnabled) {
        TextButton(onClick = { showTimePicker = true }) {
            Text("Notification time: %02d:%02d".format(state.reminderHour, state.reminderMinute))
        }
    }
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
