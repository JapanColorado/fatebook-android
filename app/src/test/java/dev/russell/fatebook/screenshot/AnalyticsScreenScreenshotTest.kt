package dev.russell.fatebook.screenshot

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import dev.russell.fatebook.ui.analytics.AnalyticsScreenContent
import dev.russell.fatebook.ui.analytics.AnalyticsUiState
import dev.russell.fatebook.ui.analytics.CalibrationBucket
import dev.russell.fatebook.ui.analytics.DayActivity
import dev.russell.fatebook.ui.theme.FatebookTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

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
                        dailyActivity = emptyHeatmap(),
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
                        dailyActivity = sampleHeatmap(),
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
                        dailyActivity = sparseHeatmap(),
                    ),
                )
            }
        }
    }

    private companion object {
        // Fixed-date window ensures reproducible goldens: Jan 27 2025 (Mon) – Apr 13 2025 (Sun).
        // All dates are safely in the past so ActivityHeatmap's future-date check is inert.
        val WINDOW_START: LocalDate = LocalDate.of(2025, 1, 27)

        fun buildHeatmap(countForOffset: (Int) -> Int): List<DayActivity> =
            (0..76).map { offset ->
                DayActivity(
                    date = WINDOW_START.plusDays(offset.toLong()),
                    count = countForOffset(offset),
                )
            }

        fun emptyHeatmap(): List<DayActivity> = buildHeatmap { 0 }

        fun sampleHeatmap(): List<DayActivity> = buildHeatmap { i ->
            // Pseudo-random-but-stable pattern showing a mix of inactive, light, and heavy days.
            when (i % 7) {
                0 -> if (i % 2 == 0) 2 else 0
                1 -> (i % 5)
                2 -> 0
                3 -> (i % 9).coerceAtLeast(1)
                4 -> if (i > 40) 6 else 1
                5 -> 3
                else -> 0
            }
        }

        fun sparseHeatmap(): List<DayActivity> = buildHeatmap { i ->
            when {
                i % 11 == 0 -> 4
                i % 5 == 0 -> 2
                i % 3 == 0 -> 1
                else -> 0
            }
        }
    }
}
