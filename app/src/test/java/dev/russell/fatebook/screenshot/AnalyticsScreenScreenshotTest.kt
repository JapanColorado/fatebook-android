package dev.russell.fatebook.screenshot

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import dev.russell.fatebook.ui.analytics.AnalyticsScreenContent
import dev.russell.fatebook.ui.analytics.AnalyticsUiState
import dev.russell.fatebook.ui.analytics.CalibrationBucket
import dev.russell.fatebook.ui.analytics.DayActivity
import dev.russell.fatebook.ui.analytics.MonthlyBrier
import dev.russell.fatebook.ui.analytics.TagBrierEntry
import java.time.YearMonth
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
                        // Folded buckets (50-100% only), varied counts so the
                        // opacity-encodes-count effect is visible. 70-75% is an
                        // intentional gap to show how empty slots render.
                        calibrationBuckets = listOf(
                            CalibrationBucket("50-55%", 0.525f, 0.48f, 6),
                            CalibrationBucket("55-60%", 0.575f, 0.55f, 4),
                            CalibrationBucket("60-65%", 0.625f, 0.71f, 9),
                            CalibrationBucket("65-70%", 0.675f, 0.62f, 1),
                            CalibrationBucket("75-80%", 0.775f, 0.72f, 14),
                            CalibrationBucket("80-85%", 0.825f, 0.80f, 25),
                            CalibrationBucket("85-90%", 0.875f, 0.95f, 2),
                            CalibrationBucket("90-95%", 0.925f, 0.90f, 7),
                            CalibrationBucket("95-100%", 0.975f, 0.96f, 3),
                        ),
                        currentStreak = 7,
                        dailyActivity = sampleHeatmap(),
                        tagBreakdown = listOf(
                            TagBrierEntry(tag = "work", brier = 0.12, questionCount = 8),
                            TagBrierEntry(tag = "health", brier = 0.31, questionCount = 3),
                            TagBrierEntry(tag = "long-shot", brier = 0.55, questionCount = 1),
                        ),
                        monthlyBrier = listOf(
                            MonthlyBrier(YearMonth.of(2025, 9), 0.42, 3),
                            MonthlyBrier(YearMonth.of(2025, 10), 0.35, 5),
                            MonthlyBrier(YearMonth.of(2025, 11), 0.51, 2),
                            MonthlyBrier(YearMonth.of(2025, 12), 0.22, 7),
                            MonthlyBrier(YearMonth.of(2026, 1), 0.18, 4),
                            MonthlyBrier(YearMonth.of(2026, 2), 0.27, 6),
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
                            CalibrationBucket("50-55%", 0.525f, 0.60f, 6),
                            CalibrationBucket("75-80%", 0.775f, 0.20f, 1),
                            CalibrationBucket("80-85%", 0.825f, 0.78f, 12),
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
