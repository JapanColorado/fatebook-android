package dev.russell.fatebook.screenshot

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import dev.russell.fatebook.ui.settings.SettingsScreenContent
import dev.russell.fatebook.ui.settings.SettingsUiState
import dev.russell.fatebook.ui.theme.FatebookTheme
import org.junit.Rule
import org.junit.Test

class SettingsScreenScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
    )

    @Test
    fun settingsScreen_initial() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(),
                )
            }
        }
    }

    @Test
    fun settingsScreen_withApiKey() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(
                        apiKey = "abc123def456",
                        validationResult = true,
                    ),
                )
            }
        }
    }

    @Test
    fun settingsScreen_validationError() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(
                        apiKey = "bad-key",
                        validationResult = false,
                        validationError = "Invalid API key — could not connect to Fatebook",
                    ),
                )
            }
        }
    }

    @Test
    fun settingsScreen_notificationsEnabled() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(
                        apiKey = "abc123",
                        validationResult = true,
                        notificationsEnabled = true,
                        reminderHour = 9,
                        reminderMinute = 0,
                    ),
                )
            }
        }
    }

    @Test
    fun settingsScreen_validating() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(
                        apiKey = "testing-key",
                        isValidating = true,
                    ),
                )
            }
        }
    }

    @Test
    fun settingsScreen_dark() {
        paparazzi.snapshot {
            FatebookTheme(darkTheme = true, dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(
                        apiKey = "abc123",
                        validationResult = true,
                        notificationsEnabled = true,
                        reminderHour = 21,
                        reminderMinute = 30,
                    ),
                )
            }
        }
    }
}
