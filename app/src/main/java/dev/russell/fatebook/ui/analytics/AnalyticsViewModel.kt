package dev.russell.fatebook.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.russell.fatebook.data.repository.QuestionRepository
import dev.russell.fatebook.domain.model.Question
import dev.russell.fatebook.domain.model.Resolution
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import javax.inject.Inject

data class CalibrationBucket(
    val rangeLabel: String,
    val predictedRate: Float,
    val actualRate: Float,
    val count: Int,
)

data class WeekActivity(
    val weekLabel: String,
    val count: Int,
)

data class AnalyticsUiState(
    val brierScore: Double? = null,
    val totalResolved: Int = 0,
    val calibrationBuckets: List<CalibrationBucket> = emptyList(),
    val currentStreak: Int = 0,
    val weeklyActivity: List<WeekActivity> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repository: QuestionRepository,
) : ViewModel() {

    val uiState: StateFlow<AnalyticsUiState> = combine(
        repository.observeAll(),
        repository.observeResolved(),
    ) { allQuestions, resolvedQuestions ->
        AnalyticsUiState(
            brierScore = computeBrierScore(resolvedQuestions),
            totalResolved = resolvedQuestions.count { it.resolution != null && it.resolution != Resolution.AMBIGUOUS && it.yourLatestForecast != null },
            calibrationBuckets = computeCalibrationBuckets(resolvedQuestions),
            currentStreak = computeStreak(allQuestions),
            weeklyActivity = computeWeeklyActivity(allQuestions),
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState())

    companion object {

        fun computeBrierScore(resolvedQuestions: List<Question>): Double? {
            val scored = resolvedQuestions.filter { q ->
                q.yourLatestForecast != null && q.resolution != null && q.resolution != Resolution.AMBIGUOUS
            }
            if (scored.isEmpty()) return null
            val sum = scored.sumOf { q ->
                val outcome = if (q.resolution == Resolution.YES) 1.0 else 0.0
                val forecast = q.yourLatestForecast!!
                (forecast - outcome) * (forecast - outcome)
            }
            return sum / scored.size
        }

        fun computeCalibrationBuckets(resolvedQuestions: List<Question>): List<CalibrationBucket> {
            val scored = resolvedQuestions.filter { q ->
                q.yourLatestForecast != null && q.resolution != null && q.resolution != Resolution.AMBIGUOUS
            }

            val bucketRanges = listOf(
                0 to 10, 10 to 20, 20 to 30, 30 to 40, 40 to 50,
                50 to 60, 60 to 70, 70 to 80, 80 to 90, 90 to 100,
            )

            return bucketRanges.mapNotNull { (low, high) ->
                val inBucket = scored.filter { q ->
                    val pct = q.yourLatestForecast!! * 100
                    if (high == 100) pct >= low && pct <= high
                    else pct >= low && pct < high
                }
                if (inBucket.isEmpty()) return@mapNotNull null

                val predictedRate = inBucket.map { it.yourLatestForecast!!.toFloat() }.average().toFloat()
                val yesCount = inBucket.count { it.resolution == Resolution.YES }
                val actualRate = yesCount.toFloat() / inBucket.size.toFloat()

                CalibrationBucket(
                    rangeLabel = "$low-$high%",
                    predictedRate = predictedRate,
                    actualRate = actualRate,
                    count = inBucket.size,
                )
            }
        }

        fun computeStreak(allQuestions: List<Question>): Int {
            val datesWithForecasts = allQuestions
                .filter { it.latestForecastAt != null }
                .map { it.latestForecastAt!!.atZone(ZoneId.systemDefault()).toLocalDate() }
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

        fun computeWeeklyActivity(allQuestions: List<Question>): List<WeekActivity> {
            val now = LocalDate.now()
            val twelveWeeksAgo = now.minusWeeks(12)
            val weekFormatter = DateTimeFormatter.ofPattern("MMM d")

            val questionsWithForecast = allQuestions.filter { it.latestForecastAt != null }

            // Build 12 weeks of buckets
            val weeks = (0 until 12).map { weeksAgo ->
                val weekStart = now.minusWeeks(11L - weeksAgo).with(java.time.DayOfWeek.MONDAY)
                weekStart
            }

            return weeks.map { weekStart ->
                val weekEnd = weekStart.plusDays(7)
                val count = questionsWithForecast.count { q ->
                    val forecastDate = q.latestForecastAt!!.atZone(ZoneId.systemDefault()).toLocalDate()
                    !forecastDate.isBefore(weekStart) && forecastDate.isBefore(weekEnd)
                }
                WeekActivity(
                    weekLabel = weekStart.format(weekFormatter),
                    count = count,
                )
            }
        }
    }
}
