package dev.russell.fatebook.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import dev.russell.fatebook.domain.model.QuestionType
import dev.russell.fatebook.domain.model.Resolution
import dev.russell.fatebook.testutil.TestData
import dev.russell.fatebook.ui.components.ErrorBanner
import dev.russell.fatebook.ui.components.ForecastSparkline
import dev.russell.fatebook.ui.components.OfflineBanner
import dev.russell.fatebook.ui.components.PieSegment
import dev.russell.fatebook.ui.components.ProbabilityPieChart
import dev.russell.fatebook.ui.components.ProbabilitySlider
import dev.russell.fatebook.ui.components.QuestionCard
import dev.russell.fatebook.ui.components.ShimmerQuestionCard
import dev.russell.fatebook.ui.components.SparklinePoint
import dev.russell.fatebook.ui.components.SyncIssuesBanner
import dev.russell.fatebook.ui.theme.FatebookTheme
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

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
    fun questionCard_withTags() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                QuestionCard(
                    question = TestData.question(
                        title = "Will I hit my deadlift PR this quarter?",
                        yourLatestForecast = 0.6,
                        resolveBy = Instant.parse("2030-12-31T00:00:00Z"),
                        tags = listOf("fitness", "goals", "long-shot"),
                    ),
                    onClick = {},
                )
            }
        }
    }

    @Test
    fun questionCard_multipleChoice_active() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                QuestionCard(
                    question = TestData.question(
                        title = "Which framework will we pick?",
                        type = QuestionType.MULTIPLE_CHOICE,
                        yourLatestForecast = null,
                        latestForecastAt = null,
                        resolveBy = Instant.parse("2030-12-31T00:00:00Z"),
                        options = listOf(
                            TestData.questionOption(id = "o1", text = "Compose", latestForecast = 0.65),
                            TestData.questionOption(id = "o2", text = "Flutter", latestForecast = 0.25),
                        ),
                    ),
                    onClick = {},
                )
            }
        }
    }

    @Test
    fun questionCard_multipleChoice_resolved() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                QuestionCard(
                    question = TestData.question(
                        title = "Which framework did we pick?",
                        type = QuestionType.MULTIPLE_CHOICE,
                        resolved = true,
                        resolution = Resolution.YES,
                        yourLatestForecast = null,
                        latestForecastAt = null,
                        options = listOf(
                            TestData.questionOption(
                                id = "o1",
                                text = "Compose",
                                resolution = Resolution.YES,
                            ),
                            TestData.questionOption(
                                id = "o2",
                                text = "Flutter",
                                resolution = Resolution.NO,
                            ),
                        ),
                    ),
                    onClick = {},
                )
            }
        }
    }

    @Test
    fun questionCard_quantity() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                QuestionCard(
                    question = TestData.question(
                        title = "How many books will I read this year?",
                        type = QuestionType.QUANTITY,
                        yourLatestForecast = null,
                        latestForecastAt = null,
                        resolveBy = Instant.parse("2030-12-31T00:00:00Z"),
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

    // Overdue / due-today styling is relative to `today`, which is injected so
    // the goldens don't drift as the calendar moves.

    @Test
    fun questionCard_overdue() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                QuestionCard(
                    question = TestData.question(
                        title = "Will the package arrive this week?",
                        yourLatestForecast = 0.6,
                        resolveBy = Instant.parse("2030-06-01T00:00:00Z"),
                    ),
                    onClick = {},
                    today = LocalDate.of(2030, 6, 5),
                )
            }
        }
    }

    @Test
    fun questionCard_overdue_dark() {
        paparazzi.snapshot {
            FatebookTheme(darkTheme = true, dynamicColor = false) {
                QuestionCard(
                    question = TestData.question(
                        title = "Will the package arrive this week?",
                        yourLatestForecast = 0.6,
                        resolveBy = Instant.parse("2030-06-01T00:00:00Z"),
                    ),
                    onClick = {},
                    today = LocalDate.of(2030, 6, 5),
                )
            }
        }
    }

    @Test
    fun questionCard_dueToday() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                QuestionCard(
                    question = TestData.question(
                        title = "Will I finish the report today?",
                        yourLatestForecast = 0.4,
                        resolveBy = Instant.parse("2030-06-01T00:00:00Z"),
                    ),
                    onClick = {},
                    today = LocalDate.of(2030, 6, 1),
                )
            }
        }
    }

    @Test
    fun questionCard_resolved_withResolvedAt() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                QuestionCard(
                    question = TestData.question(
                        title = "Did it rain yesterday?",
                        resolved = true,
                        resolution = Resolution.NO,
                        resolveBy = Instant.parse("2030-06-01T00:00:00Z"),
                        resolvedAt = Instant.parse("2030-05-28T15:00:00Z"),
                    ),
                    onClick = {},
                )
            }
        }
    }

    // --- ForecastSparkline ---

    private val risingPoints = listOf(
        SparklinePoint(0f, 0.3f),
        SparklinePoint(0.3f, 0.45f),
        SparklinePoint(0.55f, 0.4f),
        SparklinePoint(0.8f, 0.7f),
    )

    @Test
    fun forecastSparkline_rising() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                // Column gives loose height constraints, as the detail sheet does.
                Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                    ForecastSparkline(points = risingPoints, contentDescription = "Your forecasts")
                }
            }
        }
    }

    @Test
    fun forecastSparkline_dark() {
        paparazzi.snapshot {
            FatebookTheme(darkTheme = true, dynamicColor = false) {
                // Column gives loose height constraints, as the detail sheet does.
                Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                    ForecastSparkline(points = risingPoints, contentDescription = "Your forecasts")
                }
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

    // --- ProbabilityPieChart ---

    @Test
    fun probabilityPieChart_evenThreeWay() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                ProbabilityPieChart(
                    segments = listOf(
                        PieSegment("o1", "Compose", 1f / 3f),
                        PieSegment("o2", "Flutter", 1f / 3f),
                        PieSegment("o3", "React Native", 1f / 3f),
                    ),
                    onValuesChange = {},
                )
            }
        }
    }

    @Test
    fun probabilityPieChart_unevenFiveWay() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                ProbabilityPieChart(
                    segments = listOf(
                        PieSegment("o1", "Politics", 0.50f),
                        PieSegment("o2", "Operations", 0.14f),
                        PieSegment("o3", "Engineering", 0.19f),
                        PieSegment("o4", "Research", 0.10f),
                        PieSegment("o5", "Other", 0.07f),
                    ),
                    onValuesChange = {},
                )
            }
        }
    }

    @Test
    fun probabilityPieChart_tinySlice_dark() {
        paparazzi.snapshot {
            FatebookTheme(darkTheme = true, dynamicColor = false) {
                ProbabilityPieChart(
                    segments = listOf(
                        PieSegment("o1", "Yes, easily", 0.79f),
                        PieSegment("o2", "Barely", 0.19f),
                        PieSegment("o3", "No", 0.02f),
                    ),
                    onValuesChange = {},
                )
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
