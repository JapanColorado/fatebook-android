package dev.russell.fatebook.screenshot

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import dev.russell.fatebook.ui.create.CreateScreenContent
import dev.russell.fatebook.ui.create.CreateUiState
import dev.russell.fatebook.ui.theme.FatebookTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class CreateScreenScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
    )

    @Test
    fun createScreen_empty() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                CreateScreenContent(
                    state = CreateUiState(
                        resolveBy = LocalDate.of(2026, 4, 1),
                    ),
                )
            }
        }
    }

    @Test
    fun createScreen_filled() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                CreateScreenContent(
                    state = CreateUiState(
                        title = "Will I get promoted this quarter?",
                        forecast = 0.65f,
                        resolveBy = LocalDate.of(2026, 6, 30),
                    ),
                )
            }
        }
    }

    @Test
    fun createScreen_error() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                CreateScreenContent(
                    state = CreateUiState(
                        title = "",
                        error = "Title is required",
                        resolveBy = LocalDate.of(2026, 4, 1),
                    ),
                )
            }
        }
    }

    @Test
    fun createScreen_submitting() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                CreateScreenContent(
                    state = CreateUiState(
                        title = "Will it rain tomorrow?",
                        forecast = 0.7f,
                        isSubmitting = true,
                        resolveBy = LocalDate.of(2026, 4, 1),
                    ),
                )
            }
        }
    }

    @Test
    fun createScreen_dark() {
        paparazzi.snapshot {
            FatebookTheme(darkTheme = true, dynamicColor = false) {
                CreateScreenContent(
                    state = CreateUiState(
                        title = "Will I get promoted this quarter?",
                        forecast = 0.65f,
                        resolveBy = LocalDate.of(2026, 6, 30),
                    ),
                )
            }
        }
    }
}
