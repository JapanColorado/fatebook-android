package dev.russell.fatebook.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.russell.fatebook.data.local.ForecastEntity
import dev.russell.fatebook.data.preferences.UserPreferences
import dev.russell.fatebook.data.repository.QuestionRepository
import dev.russell.fatebook.domain.model.Question
import dev.russell.fatebook.domain.model.QuestionType
import dev.russell.fatebook.domain.model.Resolution
import dev.russell.fatebook.di.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
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

/** Progress of the one-off "sync full history" pull. */
data class HistorySyncState(
    val isSyncing: Boolean = false,
    val syncedCount: Int = 0,
    val isComplete: Boolean = false,
    val error: String? = null,
)

data class MonthlyBrier(
    val month: YearMonth,
    val score: Double,
    val count: Int,
)

data class AnalyticsUiState(
    val brierScore: Double? = null,
    val totalForecasts: Int = 0,
    val calibrationBuckets: List<CalibrationBucket> = emptyList(),
    val currentStreak: Int = 0,
    val dailyActivity: List<DayActivity> = emptyList(),
    val tagBreakdown: List<TagBrierEntry> = emptyList(),
    val monthlyBrier: List<MonthlyBrier> = emptyList(),
    val historySync: HistorySyncState = HistorySyncState(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repository: QuestionRepository,
    private val prefs: UserPreferences,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _historySync = MutableStateFlow(HistorySyncState())

    // The scoring pipeline is expensive (per-item day loops), so it only reacts
    // to Room changes; sync-progress ticks merge in via the trivial combine below.
    private val analytics: Flow<AnalyticsUiState> = combine(
        repository.observeAll(),
        repository.observeAllForecasts(),
    ) { allQuestions, allForecasts ->
        val forecastsByQuestion = allForecasts.groupBy { it.questionId }
        // MC options can resolve while their parent question is still open, so
        // scoring inputs come from ALL questions (the builder filters).
        val items = buildScoringInputs(allQuestions, forecastsByQuestion)

        AnalyticsUiState(
            brierScore = computeBrierScore(items),
            totalForecasts = countScoredForecasts(items),
            calibrationBuckets = computeCalibrationBuckets(items),
            currentStreak = computeStreak(allForecasts),
            dailyActivity = computeDailyActivity(allForecasts),
            tagBreakdown = computeTagBreakdown(items),
            monthlyBrier = computeMonthlyBrier(items),
            isLoading = false,
        )
    }.flowOn(defaultDispatcher)

    val uiState: StateFlow<AnalyticsUiState> = combine(analytics, _historySync) { state, sync ->
        state.copy(historySync = sync)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState())

    init {
        // First visit to Analytics kicks off the full-history pull automatically;
        // afterwards refresh() always re-fetches everything, so once is enough.
        viewModelScope.launch {
            if (prefs.fullHistorySynced.first()) {
                _historySync.update { it.copy(isComplete = true) }
            } else {
                syncFullHistory()
            }
        }
    }

    /**
     * Pull every page of questions into the Room cache so Brier/calibration
     * cover the full account history, then flip the flag that keeps refresh()
     * in full-history mode.
     */
    fun syncFullHistory() {
        if (_historySync.value.isSyncing) return
        viewModelScope.launch {
            _historySync.update {
                it.copy(isSyncing = true, syncedCount = 0, error = null)
            }
            try {
                repository.loadAllQuestions { loaded ->
                    _historySync.update { it.copy(syncedCount = loaded) }
                }
                prefs.setFullHistorySynced(true)
                _historySync.update { it.copy(isSyncing = false, isComplete = true) }
            } catch (e: Exception) {
                // Quiet failure — analytics still works over whatever is cached.
                _historySync.update {
                    it.copy(isSyncing = false, error = e.message ?: "Sync failed")
                }
            }
        }
    }

    /**
     * One independently-scored binary event: a resolved binary question, or a
     * resolved option of a multiple-choice question. Mirrors the fatebook
     * website, which scores `Question | QuestionOption` identically.
     */
    data class ScoringItem(
        val tags: List<String>,
        val scoring: BrierScoring.QuestionForScoring,
    ) {
        val resolvedAtMs: Long get() = scoring.resolvedAtMs
        val resolvedYes: Boolean get() = scoring.resolvedYes
        val rawForecasts: List<Double> get() = scoring.forecasts.map { it.forecast }

        /**
         * Cached time-weighted Brier score — the day loop is the expensive part
         * of the pipeline and every grouping (overall/tag/month) needs it.
         */
        val score: Double? by lazy(LazyThreadSafetyMode.NONE) {
            BrierScoring.questionBrierScore(scoring)
        }
    }

    companion object {

        /**
         * Builds the scoring inputs from ALL cached questions:
         * - BINARY resolved YES/NO -> one item (question-level forecasts only)
         * - MULTIPLE_CHOICE -> one item per YES/NO-resolved option, using the
         *   forecasts made on that option; the parent's resolution state is
         *   irrelevant (non-exclusive options resolve one at a time)
         * - AMBIGUOUS anything and QUANTITY are never scored
         */
        fun buildScoringInputs(
            questions: List<Question>,
            forecastsByQuestion: Map<String, List<ForecastEntity>>,
        ): List<ScoringItem> {
            val items = mutableListOf<ScoringItem>()
            for (q in questions) {
                val questionForecasts = forecastsByQuestion[q.id].orEmpty()
                when (q.type) {
                    QuestionType.BINARY -> {
                        if (q.resolution != Resolution.YES && q.resolution != Resolution.NO) continue
                        val forecasts = questionForecasts.filter { it.optionId == null }
                        if (forecasts.isEmpty()) continue
                        // resolvedAt is the canonical resolution time; fall back
                        // to resolveBy for rows cached before it was tracked.
                        val resolvedAtMs = (q.resolvedAt ?: q.resolveBy).toEpochMilli()
                        items += ScoringItem(
                            tags = q.tags,
                            scoring = scoring(
                                createdAtMs = q.createdAt.toEpochMilli(),
                                resolvedAtMs = resolvedAtMs,
                                resolvedYes = q.resolution == Resolution.YES,
                                forecasts = forecasts,
                            ),
                        )
                    }
                    QuestionType.MULTIPLE_CHOICE -> {
                        for (option in q.options) {
                            if (option.resolution != Resolution.YES &&
                                option.resolution != Resolution.NO
                            ) {
                                continue
                            }
                            val forecasts = questionForecasts.filter { it.optionId == option.id }
                            if (forecasts.isEmpty()) continue
                            val resolvedAtMs =
                                (option.resolvedAt ?: q.resolvedAt ?: q.resolveBy).toEpochMilli()
                            items += ScoringItem(
                                tags = q.tags,
                                scoring = scoring(
                                    createdAtMs = q.createdAt.toEpochMilli(),
                                    resolvedAtMs = resolvedAtMs,
                                    resolvedYes = option.resolution == Resolution.YES,
                                    forecasts = forecasts,
                                ),
                            )
                        }
                    }
                    QuestionType.QUANTITY -> Unit
                }
            }
            return items
        }

        private fun scoring(
            createdAtMs: Long,
            resolvedAtMs: Long,
            resolvedYes: Boolean,
            forecasts: List<ForecastEntity>,
        ) = BrierScoring.QuestionForScoring(
            createdAtMs = createdAtMs,
            resolvedAtMs = resolvedAtMs,
            resolvedYes = resolvedYes,
            forecasts = forecasts.map {
                BrierScoring.TimedForecast(it.createdAtEpochMs, it.forecast)
            },
        )

        fun countScoredForecasts(items: List<ScoringItem>): Int =
            items.sumOf { it.rawForecasts.size }

        /**
         * Overall Brier score matching fatebook.io: two-sided and per-item
         * time-weighted (see [BrierScoring]). Each scored item contributes
         * equally regardless of how many times it was forecasted.
         */
        fun computeBrierScore(items: List<ScoringItem>): Double? {
            val scores = items.mapNotNull { it.score }
            return if (scores.isEmpty()) null else scores.sum() / scores.size
        }

        /**
         * Folded calibration: forecasts below 50% are converted to their complement
         * (predict 1-p for the opposite outcome), so all buckets live in 50-100%.
         * A forecast of exactly 50% keeps its original orientation.
         */
        fun computeCalibrationBuckets(items: List<ScoringItem>): List<CalibrationBucket> {
            val folded = items.flatMap { item ->
                item.rawForecasts.map { forecast ->
                    if (forecast < 0.5) (1 - forecast) to !item.resolvedYes
                    else forecast to item.resolvedYes
                }
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
         * An item carrying several tags counts once under each of them.
         * Sorted best (lowest) score first.
         */
        fun computeTagBreakdown(items: List<ScoringItem>): List<TagBrierEntry> {
            val byTag = items
                .flatMap { item -> item.tags.map { tag -> tag to item } }
                .groupBy({ it.first }, { it.second })
            return byTag.mapNotNull { (tag, tagged) ->
                val brier = computeBrierScore(tagged) ?: return@mapNotNull null
                TagBrierEntry(
                    tag = tag,
                    brier = brier,
                    questionCount = tagged.size,
                )
            }.sortedBy { it.brier }
        }

        /**
         * Mean per-item Brier score bucketed by resolution month (device zone),
         * most recent 12 months with data.
         */
        fun computeMonthlyBrier(
            items: List<ScoringItem>,
            zone: ZoneId = ZoneId.systemDefault(),
        ): List<MonthlyBrier> {
            return items
                .groupBy { YearMonth.from(Instant.ofEpochMilli(it.resolvedAtMs).atZone(zone)) }
                .mapNotNull { (month, group) ->
                    val score = computeBrierScore(group) ?: return@mapNotNull null
                    MonthlyBrier(month = month, score = score, count = group.size)
                }
                .sortedBy { it.month }
                .takeLast(12)
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
