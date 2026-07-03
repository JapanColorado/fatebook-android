package dev.russell.fatebook.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.russell.fatebook.data.local.ForecastEntity
import dev.russell.fatebook.data.repository.QuestionRepository
import dev.russell.fatebook.domain.model.Question
import dev.russell.fatebook.domain.model.QuestionType
import dev.russell.fatebook.domain.model.Resolution
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

data class TagBrierEntry(
    val tag: String,
    val brier: Double,
    val questionCount: Int,
)

data class AnalyticsUiState(
    val brierScore: Double? = null,
    val totalForecasts: Int = 0,
    val calibrationBuckets: List<CalibrationBucket> = emptyList(),
    val currentStreak: Int = 0,
    val dailyActivity: List<DayActivity> = emptyList(),
    val tagBreakdown: List<TagBrierEntry> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repository: QuestionRepository,
) : ViewModel() {

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
            tagBreakdown = computeTagBreakdown(resolvedQuestions, forecastsByQuestion),
            isLoading = false,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState())

    companion object {

        /**
         * Pairs each forecast with its question's resolution for scoring.
         * Binary questions only: a resolved MC question's parent resolution is
         * YES whenever any option won, and its ForecastEntity rows belong to
         * options — scoring them here would corrupt Brier/calibration.
         * (Per-option MC scoring, matching the website, is a follow-up.)
         */
        private fun scoredPairs(
            resolvedQuestions: List<Question>,
            forecastsByQuestion: Map<String, List<ForecastEntity>>,
        ): List<Pair<Double, Resolution>> {
            return resolvedQuestions
                .filter { it.type == QuestionType.BINARY }
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

        /**
         * Overall Brier score matching fatebook.io: two-sided and per-question
         * time-weighted (see [BrierScoring]). Each scored question contributes
         * equally regardless of how many times it was forecasted.
         */
        fun computeBrierScore(
            resolvedQuestions: List<Question>,
            forecastsByQuestion: Map<String, List<ForecastEntity>>,
        ): Double? {
            val scoringQuestions = resolvedQuestions
                .filter { it.type == QuestionType.BINARY }
                .filter { it.resolution == Resolution.YES || it.resolution == Resolution.NO }
                .mapNotNull { q ->
                    val forecasts = forecastsByQuestion[q.id]
                        ?.map { BrierScoring.TimedForecast(it.createdAtEpochMs, it.forecast) }
                        ?: emptyList()
                    if (forecasts.isEmpty()) return@mapNotNull null
                    // resolvedAt is the canonical resolution time; fall back to
                    // resolveBy for rows cached before resolvedAt was tracked.
                    val resolvedAtMs = (q.resolvedAt ?: q.resolveBy).toEpochMilli()
                    BrierScoring.QuestionForScoring(
                        createdAtMs = q.createdAt.toEpochMilli(),
                        resolvedAtMs = resolvedAtMs,
                        resolvedYes = q.resolution == Resolution.YES,
                        forecasts = forecasts,
                    )
                }
            return BrierScoring.overallBrierScore(scoringQuestions)
        }

        /**
         * Folded calibration: forecasts below 50% are converted to their complement
         * (predict 1-p for the opposite outcome), so all buckets live in 50-100%.
         * A forecast of exactly 50% keeps its original orientation.
         */
        fun computeCalibrationBuckets(
            resolvedQuestions: List<Question>,
            forecastsByQuestion: Map<String, List<ForecastEntity>>,
        ): List<CalibrationBucket> {
            val folded = scoredPairs(resolvedQuestions, forecastsByQuestion)
                .map { (forecast, resolution) ->
                    if (forecast < 0.5) (1 - forecast) to (resolution == Resolution.NO)
                    else forecast to (resolution == Resolution.YES)
                }

            val bucketSize = 5
            val bucketRanges = (50 until 100 step bucketSize).map { it to it + bucketSize }

            return bucketRanges.mapNotNull { (low, high) ->
                val inBucket = folded.filter { (forecast, _) ->
                    val pct = forecast * 100
                    if (high == 100) pct >= low && pct <= high
                    else pct >= low && pct < high
                }
                if (inBucket.isEmpty()) return@mapNotNull null

                val bucketCenter = (low + high) / 2f / 100f
                val hitCount = inBucket.count { (_, hit) -> hit }
                val actualRate = hitCount.toFloat() / inBucket.size.toFloat()

                CalibrationBucket(
                    rangeLabel = "$low-$high%",
                    predictedRate = bucketCenter,
                    actualRate = actualRate,
                    count = inBucket.size,
                )
            }
        }

        /**
         * Per-tag Brier scores over the same inputs as the overall score.
         * A question carrying several tags counts once under each of them.
         * Sorted best (lowest) score first.
         */
        fun computeTagBreakdown(
            resolvedQuestions: List<Question>,
            forecastsByQuestion: Map<String, List<ForecastEntity>>,
        ): List<TagBrierEntry> {
            val tagged = resolvedQuestions.filter { it.tags.isNotEmpty() }
            val byTag = tagged
                .flatMap { q -> q.tags.map { tag -> tag to q } }
                .groupBy({ it.first }, { it.second })
            return byTag.mapNotNull { (tag, questions) ->
                val brier = computeBrierScore(questions, forecastsByQuestion)
                    ?: return@mapNotNull null
                TagBrierEntry(
                    tag = tag,
                    brier = brier,
                    questionCount = questions
                        .count {
                            it.type == QuestionType.BINARY &&
                                (it.resolution == Resolution.YES || it.resolution == Resolution.NO) &&
                                !forecastsByQuestion[it.id].isNullOrEmpty()
                        },
                )
            }.sortedBy { it.brier }
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
