package dev.russell.fatebook.screenshot

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import dev.russell.fatebook.ui.onboarding.OnboardingScreenContent
import dev.russell.fatebook.ui.onboarding.OnboardingUiState
import dev.russell.fatebook.ui.onboarding.OnboardingViewModel
import dev.russell.fatebook.ui.theme.FatebookTheme
import org.junit.Rule
import org.junit.Test

class OnboardingScreenScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
    )

    private fun snapshot(state: OnboardingUiState, dark: Boolean = false) {
        paparazzi.snapshot {
            FatebookTheme(darkTheme = dark) {
                OnboardingScreenContent(state = state)
            }
        }
    }

    @Test
    fun onboarding_welcome() = snapshot(OnboardingUiState())

    @Test
    fun onboarding_welcome_dark() = snapshot(OnboardingUiState(), dark = true)

    @Test
    fun onboarding_apiKey_empty() =
        snapshot(OnboardingUiState(page = OnboardingViewModel.PAGE_API_KEY))

    @Test
    fun onboarding_apiKey_connecting() =
        snapshot(
            OnboardingUiState(
                page = OnboardingViewModel.PAGE_API_KEY,
                apiKey = "abc123def456",
                isConnecting = true,
            ),
        )

    @Test
    fun onboarding_apiKey_error() =
        snapshot(
            OnboardingUiState(
                page = OnboardingViewModel.PAGE_API_KEY,
                apiKey = "bad-key",
                connectError = "Invalid API key — could not connect to Fatebook",
            ),
        )

    @Test
    fun onboarding_notifications_enabled() =
        snapshot(
            OnboardingUiState(
                page = OnboardingViewModel.PAGE_NOTIFICATIONS,
                connected = true,
                notificationsEnabled = true,
                reminderHour = 20,
                reminderMinute = 0,
            ),
        )
}
