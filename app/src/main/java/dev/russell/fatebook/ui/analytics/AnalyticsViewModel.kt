package dev.russell.fatebook.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.russell.fatebook.data.local.ForecastEntity
import dev.russell.fatebook.data.repository.QuestionRepository
import dev.russell.fatebook.domain.model.Question
import dev.russell.fatebook.domain.model.Resolution
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class CalibrationBucket(
    val rangeLabel: String,
    val predictedRate: Float,
    val actualRate: Float,
    val count: Int,
)

data class DayActivity(
    val date: LocalDate,
    val count: Int,
)

data class AnalyticsUiState(
    val brierScore: Double? = null,
    val totalForecasts: Int = 0,
    val calibrationBuckets: List<CalibrationBucket> = emptyList(),
    val currentStreak: Int = 0,
    val dailyActivity: List<DayActivity> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repository: QuestionRepository,
) : ViewModel() {

    init {
        viewModelScope.launch {
            try {
                repository.loadAllQuestions()
            } catch (_: Exception) {
                // Analytics can still show cached data if network fails
            }
        }
    }

    val uiState: StateFlow<AnalyticsUiState> = combine(
        repository.observeAll(),
        repository.observeResolved(),
        repository.observeAllForecasts(),
    ) { allQuestions, resolvedQuestions, allForecasts ->
        val forecastsByQuestion = allForecasts.groupBy { it.questionId }

        AnalyticsUiState(
            brierScore = computeBrierScore(resolvedQuestions, forecastsByQuestion),
            totalForecasts = countScoredForecasts(resolvedQuestions, forecastsByQuestion),
            calibrationBuckets = computeCalibrationBuckets(resolvedQuestions, forecastsByQuestion),
            currentStreak = computeStreak(allForecasts),
            dailyActivity = computeDailyActivity(allForecasts),
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState())

    companion object {

        /** Pairs each forecast with its question's resolution for scoring. */
        private fun scoredPairs(
            resolvedQuestions: List<Question>,
            forecastsByQuestion: Map<String, List<ForecastEntity>>,
        ): List<Pair<Double, Resolution>> {
            return resolvedQuestions
                .filter { it.resolution != null && it.resolution != Resolution.AMBIGUOUS }
                .flatMap { q ->
                    val forecasts = forecastsByQuestion[q.id] ?: emptyList()
                    forecasts.map { it.forecast to q.resolution!! }
                }
        }

        fun countScoredForecasts(
            resolvedQuestions: List<Question>,
            forecastsByQuestion: Map<String, List<ForecastEntity>>,
        ): Int = scoredPairs(resolvedQuestions, forecastsByQuestion).size

        fun computeBrierScore(
            resolvedQuestions: List<Question>,
            forecastsByQuestion: Map<String, List<ForecastEntity>>,
        ): Double? {
            val pairs = scoredPairs(resolvedQuestions, forecastsByQuestion)
            if (pairs.isEmpty()) return null
            val sum = pairs.sumOf { (forecast, resolution) ->
                val outcome = if (resolution == Resolution.YES) 1.0 else 0.0
                (forecast - outcome) * (forecast - outcome)
            }
            return sum / pairs.size
        }

        fun computeCalibrationBuckets(
            resolvedQuestions: List<Question>,
            forecastsByQuestion: Map<String, List<ForecastEntity>>,
        ): List<CalibrationBucket> {
            val pairs = scoredPairs(resolvedQuestions, forecastsByQuestion)

            val bucketSize = 5
            val bucketRanges = (0 until 100 step bucketSize).map { it to it + bucketSize }

            return bucketRanges.mapNotNull { (low, high) ->
                val inBucket = pairs.filter { (forecast, _) ->
                    val pct = forecast * 100
                    if (high == 100) pct >= low && pct <= high
                    else pct >= low && pct < high
                }
                if (inBucket.isEmpty()) return@mapNotNull null

                val bucketCenter = (low + high) / 2f / 100f
                val yesCount = inBucket.count { (_, resolution) -> resolution == Resolution.YES }
                val actualRate = yesCount.toFloat() / inBucket.size.toFloat()

                CalibrationBucket(
                    rangeLabel = "$low-$high%",
                    predictedRate = bucketCenter,
                    actualRate = actualRate,
                    count = inBucket.size,
                )
            }
        }

        fun computeStreak(allForecasts: List<ForecastEntity>): Int {
            val datesWithForecasts = allForecasts
                .map {
                    Instant.ofEpochMilli(it.createdAtEpochMs)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                }
                .toSet()

            if (datesWithForecasts.isEmpty()) return 0

            var streak = 0
            var day = LocalDate.now()
            while (datesWithForecasts.contains(day)) {
                streak++
                day = day.minusDays(1)
            }
            return streak
        }

        /**
         * Returns a 77-day window (11 weeks × 7 days) ending at the Sunday of the current week.
         *
         * Aligning to whole Monday–Sunday weeks produces a rectangular 11×7 grid that the
         * heatmap can render with clean weekday row labels. Today falls within the *last* column.
         */
        fun computeDailyActivity(allForecasts: List<ForecastEntity>): List<DayActivity> {
            val today = LocalDate.now()
            val zone = ZoneId.systemDefault()

            // Find the Sunday that ends the current week (or today if today is Sunday).
            val daysUntilSunday = (java.time.DayOfWeek.SUNDAY.value - today.dayOfWeek.value + 7) % 7
            val windowEnd = today.plusDays(daysUntilSunday.toLong())
            val windowStart = windowEnd.minusDays(76)

            val countsByDate: Map<LocalDate, Int> = allForecasts
                .groupingBy { f ->
                    Instant.ofEpochMilli(f.createdAtEpochMs).atZone(zone).toLocalDate()
                }
                .eachCount()

            return (0..76).map { offset ->
                val date = windowStart.plusDays(offset.toLong())
                DayActivity(date = date, count = countsByDate[date] ?: 0)
            }
        }
    }
}
