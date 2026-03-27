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
                        totalForecasts = 0,
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
                        totalForecasts = 42,
                        calibrationBuckets = listOf(
                            CalibrationBucket("0-5%", 0.025f, 0.04f, 3),
                            CalibrationBucket("5-10%", 0.075f, 0.10f, 4),
                            CalibrationBucket("10-15%", 0.125f, 0.08f, 5),
                            CalibrationBucket("15-20%", 0.175f, 0.18f, 3),
                            CalibrationBucket("25-30%", 0.275f, 0.30f, 4),
                            CalibrationBucket("35-40%", 0.375f, 0.40f, 3),
                            CalibrationBucket("45-50%", 0.475f, 0.50f, 6),
                            CalibrationBucket("55-60%", 0.575f, 0.55f, 4),
                            CalibrationBucket("65-70%", 0.675f, 0.62f, 5),
                            CalibrationBucket("75-80%", 0.775f, 0.72f, 7),
                            CalibrationBucket("80-85%", 0.825f, 0.80f, 5),
                            CalibrationBucket("90-95%", 0.925f, 0.90f, 3),
                            CalibrationBucket("95-100%", 0.975f, 0.96f, 2),
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
                        totalForecasts = 15,
                        calibrationBuckets = listOf(
                            CalibrationBucket("20-25%", 0.225f, 0.20f, 4),
                            CalibrationBucket("50-55%", 0.525f, 0.60f, 6),
                            CalibrationBucket("80-85%", 0.825f, 0.78f, 5),
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
