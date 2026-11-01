package dev.russell.fatebook.ui.onboarding

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.russell.fatebook.ui.components.LedgerMargin
import dev.russell.fatebook.ui.components.LedgerRule
import dev.russell.fatebook.ui.components.MastheadRule
import dev.russell.fatebook.ui.components.ProbabilityBar
import dev.russell.fatebook.ui.components.ReminderTimeDialog
import dev.russell.fatebook.ui.components.rememberNotificationPermissionRequester
import dev.russell.fatebook.ui.theme.Ledger
import dev.russell.fatebook.ui.theme.forecastColor

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
                    .padding(horizontal = LedgerMargin, vertical = 32.dp),
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
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = LedgerMargin, vertical = 12.dp),
    ) {
        PageProgress(count = ONBOARDING_PAGE_COUNT, current = state.page)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.page > 0) {
                TextButton(onClick = onBack, modifier = Modifier.height(52.dp)) { Text("Back") }
            }
            val primaryModifier = Modifier
                .weight(1f)
                .height(52.dp)
            val shape = MaterialTheme.shapes.medium
            when (state.page) {
                OnboardingViewModel.PAGE_WELCOME -> Button(
                    onClick = onNext,
                    modifier = primaryModifier,
                    shape = shape,
                ) { Text("Get started") }
                OnboardingViewModel.PAGE_API_KEY -> if (state.connected) {
                    Button(onClick = onNext, modifier = primaryModifier, shape = shape) { Text("Next") }
                } else {
                    Button(
                        onClick = onConnect,
                        modifier = primaryModifier,
                        shape = shape,
                        enabled = state.apiKey.isNotBlank() && !state.isConnecting,
                    ) {
                        if (state.isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Connect")
                        }
                    }
                }
                else -> Button(onClick = onFinish, modifier = primaryModifier, shape = shape) {
                    Text(if (state.notificationsEnabled) "Finish" else "Skip for now")
                }
            }
        }
    }
}

/** One thick rule per page, inked in as you progress, with the folio beside it. */
@Composable
private fun PageProgress(count: Int, current: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "Page ${current + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .background(
                        if (index <= current) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            Ledger.colors.rule
                        },
                    ),
            )
        }
        Text(
            text = "${current + 1}/$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun PageHeading(eyebrow: String, title: String, body: String) {
    Text(
        text = eyebrow.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.headlineLarge,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun WelcomePage() {
    Text(
        text = "Fatebook",
        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 52.sp, lineHeight = 56.sp),
        modifier = Modifier.semantics { heading() },
    )
    MastheadRule(Modifier.padding(top = 4.dp))
    Spacer(Modifier.height(16.dp))
    Text(
        text = "Write down what you think will happen. Say how sure you are. Find out how well-calibrated you really are.",
        style = MaterialTheme.typography.titleLarge.copy(
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Normal,
        ),
    )
    Spacer(Modifier.height(28.dp))
    Specimen()
    Spacer(Modifier.height(28.dp))
    FeatureRow(
        numeral = "1",
        title = "Predict",
        body = "Jot down a question, a resolve-by date and a probability in seconds, even offline.",
    )
    LedgerRule()
    FeatureRow(
        numeral = "2",
        title = "Resolve",
        body = "When the date comes, mark it YES or NO, straight from a notification if you like.",
    )
    LedgerRule()
    FeatureRow(
        numeral = "3",
        title = "Calibrate",
        body = "See your Brier score, calibration curve and streak grow over time.",
    )
}

/** A sample ledger entry, static so it can't age: shows what you're about to be writing. */
@Composable
private fun Specimen() {
    val color = forecastColor(0.7)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = "Example entry: Will it rain on my wedding day? 70 percent."
            },
    ) {
        LedgerRule()
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Will it rain on my wedding day?",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "70%",
                    style = MaterialTheme.typography.displaySmall,
                    color = color,
                )
            }
            ProbabilityBar(fractions = listOf(0.7f to color))
            Text(
                text = "IN 3 MONTHS · #LIFE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LedgerRule()
    }
}

@Composable
private fun FeatureRow(
    numeral: String,
    title: String,
    body: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = numeral,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp),
        )
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
    PageHeading(
        eyebrow = "Your account",
        title = "Connect your account",
        body = "The app talks to fatebook.io with a personal API key. Create one on the website, then paste it below. It's stored encrypted on this device only.",
    )
    Spacer(Modifier.height(24.dp))
    OutlinedButton(
        onClick = onOpenApiSetup,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
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
                    color = Ledger.colors.yes,
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

    PageHeading(
        eyebrow = "Notifications",
        title = "A daily nudge",
        body = "Once a day, Fatebook can remind you to make a prediction and tell you which questions are ready to resolve. Each kind can be tuned in Settings later.",
    )
    Spacer(Modifier.height(24.dp))
    LedgerRule()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = state.notificationsEnabled,
                role = Role.Switch,
                onValueChange = onNotificationsEnabledChanged,
            )
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Enable notifications", style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = state.notificationsEnabled,
            onCheckedChange = null,
        )
    }
    LedgerRule()
    if (state.notificationsEnabled) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { showTimePicker = true }
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Notification time", style = MaterialTheme.typography.bodyLarge)
            Text(
                "%02d:%02d".format(state.reminderHour, state.reminderMinute),
                style = MaterialTheme.typography.displaySmall,
            )
        }
        LedgerRule()
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
