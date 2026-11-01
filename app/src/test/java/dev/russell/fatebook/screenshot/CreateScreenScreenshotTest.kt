package dev.russell.fatebook.screenshot

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import dev.russell.fatebook.domain.model.QuestionTemplate
import dev.russell.fatebook.ui.create.CreateScreenContent
import dev.russell.fatebook.ui.create.CreateUiState
import dev.russell.fatebook.ui.theme.FatebookTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

private val TEST_TODAY = java.time.LocalDate.of(2026, 3, 14)

class CreateScreenScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
    )

    @Test
    fun createScreen_empty() {
        paparazzi.snapshot {
            FatebookTheme(darkTheme = false) {
                CreateScreenContent(
                    today = TEST_TODAY,
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
            FatebookTheme(darkTheme = false) {
                CreateScreenContent(
                    today = TEST_TODAY,
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
    fun createScreen_withTemplates() {
        paparazzi.snapshot {
            FatebookTheme(darkTheme = false) {
                CreateScreenContent(
                    today = TEST_TODAY,
                    state = CreateUiState(
                        title = "Gym 3x this week?",
                        forecast = 0.7f,
                        tags = listOf("health"),
                        resolveBy = LocalDate.of(2026, 4, 8),
                        templates = listOf(
                            QuestionTemplate(1, "Gym 3x this week?", 0.7, listOf("health"), 7),
                            QuestionTemplate(2, "Inbox zero by Friday?", 0.4, emptyList(), 5),
                            QuestionTemplate(3, "Ship the release on time?", 0.55, listOf("work"), 14),
                        ),
                    ),
                )
            }
        }
    }

    @Test
    fun createScreen_error() {
        paparazzi.snapshot {
            FatebookTheme(darkTheme = false) {
                CreateScreenContent(
                    today = TEST_TODAY,
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
            FatebookTheme(darkTheme = false) {
                CreateScreenContent(
                    today = TEST_TODAY,
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
            FatebookTheme(darkTheme = true) {
                CreateScreenContent(
                    today = TEST_TODAY,
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
