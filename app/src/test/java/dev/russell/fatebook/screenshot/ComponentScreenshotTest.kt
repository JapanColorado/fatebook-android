package dev.russell.fatebook.screenshot

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import dev.russell.fatebook.domain.model.Resolution
import dev.russell.fatebook.testutil.TestData
import dev.russell.fatebook.ui.components.ErrorBanner
import dev.russell.fatebook.ui.components.OfflineBanner
import dev.russell.fatebook.ui.components.ProbabilitySlider
import dev.russell.fatebook.ui.components.QuestionCard
import dev.russell.fatebook.ui.components.ShimmerQuestionCard
import dev.russell.fatebook.ui.components.SyncIssuesBanner
import dev.russell.fatebook.ui.theme.FatebookTheme
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class ComponentScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
    )

    // --- QuestionCard ---

    @Test
    fun questionCard_active_highForecast() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                QuestionCard(
                    question = TestData.question(
                        title = "Will GPT-5 be released by 2026?",
                        yourLatestForecast = 0.85,
                        resolveBy = Instant.parse("2030-12-31T00:00:00Z"),
                    ),
                    onClick = {},
                )
            }
        }
    }

    @Test
    fun questionCard_active_lowForecast() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                QuestionCard(
                    question = TestData.question(
                        title = "Will we colonize Mars by 2030?",
                        yourLatestForecast = 0.12,
                        resolveBy = Instant.parse("2030-12-31T00:00:00Z"),
                    ),
                    onClick = {},
                )
            }
        }
    }

    @Test
    fun questionCard_active_mediumForecast() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                QuestionCard(
                    question = TestData.question(
                        title = "Will I finish this book by Friday?",
                        yourLatestForecast = 0.50,
                        resolveBy = Instant.parse("2030-06-15T00:00:00Z"),
                    ),
                    onClick = {},
                )
            }
        }
    }

    @Test
    fun questionCard_resolved_yes() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                QuestionCard(
                    question = TestData.question(
                        title = "Did it rain yesterday?",
                        resolved = true,
                        resolution = Resolution.YES,
                        yourLatestForecast = 0.7,
                    ),
                    onClick = {},
                )
            }
        }
    }

    @Test
    fun questionCard_resolved_no() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                QuestionCard(
                    question = TestData.question(
                        title = "Will the stock market crash this week?",
                        resolved = true,
                        resolution = Resolution.NO,
                        yourLatestForecast = 0.15,
                    ),
                    onClick = {},
                )
            }
        }
    }

    @Test
    fun questionCard_resolved_ambiguous() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                QuestionCard(
                    question = TestData.question(
                        title = "Will the project launch on time?",
                        resolved = true,
                        resolution = Resolution.AMBIGUOUS,
                        yourLatestForecast = 0.5,
                    ),
                    onClick = {},
                )
            }
        }
    }

    @Test
    fun questionCard_hiddenForecast() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                QuestionCard(
                    question = TestData.question(
                        title = "Secret prediction — forecast hidden",
                        forecastHiddenUntil = Instant.parse("2099-01-01T00:00:00Z"),
                    ),
                    onClick = {},
                )
            }
        }
    }

    @Test
    fun questionCard_longTitle() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                QuestionCard(
                    question = TestData.question(
                        title = "Will the extremely long prediction title that goes on and on and on and on be truncated properly in the card UI?",
                        yourLatestForecast = 0.65,
                    ),
                    onClick = {},
                )
            }
        }
    }

    // --- QuestionCard dark theme ---

    @Test
    fun questionCard_active_dark() {
        paparazzi.snapshot {
            FatebookTheme(darkTheme = true, dynamicColor = false) {
                QuestionCard(
                    question = TestData.question(
                        title = "Will GPT-5 be released by 2026?",
                        yourLatestForecast = 0.85,
                    ),
                    onClick = {},
                )
            }
        }
    }

    @Test
    fun questionCard_resolved_dark() {
        paparazzi.snapshot {
            FatebookTheme(darkTheme = true, dynamicColor = false) {
                QuestionCard(
                    question = TestData.question(
                        title = "Did it rain yesterday?",
                        resolved = true,
                        resolution = Resolution.YES,
                    ),
                    onClick = {},
                )
            }
        }
    }

    // --- ProbabilitySlider ---

    @Test
    fun probabilitySlider_low() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                ProbabilitySlider(value = 0.10f, onValueChange = {})
            }
        }
    }

    @Test
    fun probabilitySlider_medium() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                ProbabilitySlider(value = 0.50f, onValueChange = {})
            }
        }
    }

    @Test
    fun probabilitySlider_high() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                ProbabilitySlider(value = 0.90f, onValueChange = {})
            }
        }
    }

    // --- ShimmerQuestionCard ---

    @Test
    fun shimmerQuestionCard() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                ShimmerQuestionCard()
            }
        }
    }

    // --- ErrorBanner ---

    @Test
    fun errorBanner_networkError() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                ErrorBanner(
                    message = "Unable to connect. Check your internet connection.",
                    onRetry = {},
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun errorBanner_dark() {
        paparazzi.snapshot {
            FatebookTheme(darkTheme = true, dynamicColor = false) {
                ErrorBanner(
                    message = "Unable to connect. Check your internet connection.",
                    onRetry = {},
                    onDismiss = {},
                )
            }
        }
    }

    // --- OfflineBanner ---

    @Test
    fun offlineBanner_light() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                OfflineBanner()
            }
        }
    }

    @Test
    fun offlineBanner_dark() {
        paparazzi.snapshot {
            FatebookTheme(darkTheme = true, dynamicColor = false) {
                OfflineBanner()
            }
        }
    }

    // --- SyncIssuesBanner ---

    @Test
    fun syncIssuesBanner_single() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                SyncIssuesBanner(count = 1, onView = {})
            }
        }
    }

    @Test
    fun syncIssuesBanner_multiple() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                SyncIssuesBanner(count = 3, onView = {})
            }
        }
    }
}
