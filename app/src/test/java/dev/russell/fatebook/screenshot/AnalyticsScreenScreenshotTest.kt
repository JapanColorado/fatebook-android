package dev.russell.fatebook.screenshot

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import dev.russell.fatebook.ui.analytics.AnalyticsScreenContent
import dev.russell.fatebook.ui.analytics.AnalyticsUiState
import dev.russell.fatebook.ui.analytics.CalibrationBucket
import dev.russell.fatebook.ui.analytics.WeekActivity
import dev.russell.fatebook.ui.theme.FatebookTheme
import org.junit.Rule
import org.junit.Test

class AnalyticsScreenScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
    )

    @Test
    fun analyticsScreen_loading() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                AnalyticsScreenContent(
                    state = AnalyticsUiState(isLoading = true),
                )
            }
        }
    }

    @Test
    fun analyticsScreen_emptyData() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                AnalyticsScreenContent(
                    state = AnalyticsUiState(
                        isLoading = false,
                        brierScore = null,
                        totalResolved = 0,
                        calibrationBuckets = emptyList(),
                        currentStreak = 0,
                        weeklyActivity = (0 until 12).map {
                            WeekActivity(weekLabel = "W$it", count = 0)
                        },
                    ),
                )
            }
        }
    }

    @Test
    fun analyticsScreen_withData() {
        paparazzi.snapshot {
            FatebookTheme(dynamicColor = false) {
                AnalyticsScreenContent(
                    state = AnalyticsUiState(
                        isLoading = false,
                        brierScore = 0.18,
                        totalResolved = 42,
                        calibrationBuckets = listOf(
                            CalibrationBucket("0-10%", 0.05f, 0.08f, 5),
                            CalibrationBucket("10-20%", 0.15f, 0.12f, 8),
                            CalibrationBucket("20-30%", 0.25f, 0.30f, 6),
                            CalibrationBucket("40-50%", 0.45f, 0.50f, 10),
                            CalibrationBucket("60-70%", 0.65f, 0.58f, 7),
                            CalibrationBucket("70-80%", 0.75f, 0.72f, 12),
                            CalibrationBucket("80-90%", 0.85f, 0.80f, 9),
                            CalibrationBucket("90-100%", 0.95f, 0.93f, 4),
                        ),
                        currentStreak = 7,
                        weeklyActivity = listOf(
                            WeekActivity("Jan 6", 3),
                            WeekActivity("Jan 13", 5),
                            WeekActivity("Jan 20", 2),
                            WeekActivity("Jan 27", 7),
                            WeekActivity("Feb 3", 4),
                            WeekActivity("Feb 10", 6),
                            WeekActivity("Feb 17", 1),
                            WeekActivity("Feb 24", 8),
                            WeekActivity("Mar 3", 5),
                            WeekActivity("Mar 10", 3),
                            WeekActivity("Mar 17", 9),
                            WeekActivity("Mar 24", 6),
                        ),
                    ),
                )
            }
        }
    }

    @Test
    fun analyticsScreen_dark() {
        paparazzi.snapshot {
            FatebookTheme(darkTheme = true, dynamicColor = false) {
                AnalyticsScreenContent(
                    state = AnalyticsUiState(
                        isLoading = false,
                        brierScore = 0.22,
                        totalResolved = 15,
                        calibrationBuckets = listOf(
                            CalibrationBucket("20-30%", 0.25f, 0.20f, 4),
                            CalibrationBucket("50-60%", 0.55f, 0.60f, 6),
                            CalibrationBucket("80-90%", 0.85f, 0.78f, 5),
                        ),
                        currentStreak = 3,
                        weeklyActivity = listOf(
                            WeekActivity("Jan 6", 2),
                            WeekActivity("Jan 13", 4),
                            WeekActivity("Jan 20", 1),
                            WeekActivity("Jan 27", 3),
                            WeekActivity("Feb 3", 5),
                            WeekActivity("Feb 10", 2),
                            WeekActivity("Feb 17", 0),
                            WeekActivity("Feb 24", 3),
                            WeekActivity("Mar 3", 4),
                            WeekActivity("Mar 10", 1),
                            WeekActivity("Mar 17", 6),
                            WeekActivity("Mar 24", 2),
                        ),
                    ),
                )
            }
        }
    }
}
