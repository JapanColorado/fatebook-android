package dev.russell.fatebook.ui.analytics

import com.google.common.truth.Truth.assertThat
import dev.russell.fatebook.data.local.ForecastEntity
import dev.russell.fatebook.data.preferences.UserPreferences
import dev.russell.fatebook.data.repository.QuestionRepository
import dev.russell.fatebook.domain.model.Question
import dev.russell.fatebook.domain.model.QuestionType
import dev.russell.fatebook.domain.model.Resolution
import dev.russell.fatebook.testutil.TestData
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<QuestionRepository>(relaxed = true)
    private val prefs = mockk<UserPreferences>(relaxed = true)

    private val base: Instant = Instant.parse("2024-01-01T00:00:00Z")
    private val dayMs = 1000L * 60 * 60 * 24

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { repository.observeAll() } returns flowOf(emptyList())
        every { repository.observeAllForecasts() } returns flowOf(emptyList())
        // Already synced by default so init doesn't kick off the history pull.
        every { prefs.fullHistorySynced } returns flowOf(true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): AnalyticsViewModel {
        return AnalyticsViewModel(repository, prefs, testDispatcher)
    }

    /** Shorthand for building scoring items straight from questions + forecasts. */
    private fun scoringItems(
        questions: List<Question>,
        forecasts: Map<String, List<ForecastEntity>>,
    ) = AnalyticsViewModel.buildScoringInputs(questions, forecasts)

    private fun forecast(questionId: String, value: Double, createdAtMs: Long = base.toEpochMilli()) =
        ForecastEntity(questionId = questionId, forecast = value, createdAtEpochMs = createdAtMs)

    /** A resolved question with a known 10-day [base, base+10d] scoring window. */
    private fun resolvedQuestion(
        id: String,
        resolution: Resolution,
        createdAt: Instant = base,
        resolvedAt: Instant? = base.plusMillis(10 * dayMs),
    ) = TestData.question(
        id = id,
        resolved = true,
        resolution = resolution,
        createdAt = createdAt,
        resolvedAt = resolvedAt,
    )

    // --- Brier Score (two-sided, time-weighted; matches fatebook.io) ---

    @Test
    fun `brier score uses the two-sided fatebook formula`() {
        // 0.8 held the whole window, resolved YES -> two-sided brier = 0.08
        // (the old one-sided value was 0.04 — half of this).
        val questions = listOf(resolvedQuestion("q1", Resolution.YES))
        val forecasts = mapOf("q1" to listOf(forecast("q1", 0.8)))
        val score = AnalyticsViewModel.computeBrierScore(scoringItems(questions, forecasts))
        assertThat(score).isWithin(0.0001).of(0.08)
    }

    @Test
    fun `brier score time-weights multiple forecasts on the same question`() {
        // 0.6 for days 0-5 then 0.9 for days 5-10, resolved YES.
        // (5*brier(0.6) + 5*brier(0.9)) / 10 = (5*0.32 + 5*0.02)/10 = 0.17
        val questions = listOf(resolvedQuestion("q1", Resolution.YES))
        val forecasts = mapOf(
            "q1" to listOf(
                forecast("q1", 0.6, base.toEpochMilli()),
                forecast("q1", 0.9, base.plusMillis(5 * dayMs).toEpochMilli()),
            ),
        )
        val score = AnalyticsViewModel.computeBrierScore(scoringItems(questions, forecasts))
        assertThat(score).isWithin(0.0001).of(0.17)
    }

    @Test
    fun `brier score weights each question equally`() {
        // q1: 0.8 YES -> 0.08 ; q2: 0.3 NO -> 0.18 ; mean = 0.13
        val questions = listOf(
            resolvedQuestion("q1", Resolution.YES),
            resolvedQuestion("q2", Resolution.NO),
        )
        val forecasts = mapOf(
            "q1" to listOf(forecast("q1", 0.8)),
            "q2" to listOf(forecast("q2", 0.3)),
        )
        val score = AnalyticsViewModel.computeBrierScore(scoringItems(questions, forecasts))
        assertThat(score).isWithin(0.0001).of(0.13)
    }

    @Test
    fun `brier score falls back to resolveBy when resolvedAt is missing`() {
        // Cached rows synced before resolvedAt was tracked: resolveBy is the window end.
        val resolveBy = base.plusMillis(10 * dayMs)
        val q = TestData.question(
            id = "q1",
            resolved = true,
            resolution = Resolution.YES,
            createdAt = base,
            resolveBy = resolveBy,
            resolvedAt = null,
        )
        val forecasts = mapOf("q1" to listOf(forecast("q1", 0.8)))
        val score = AnalyticsViewModel.computeBrierScore(scoringItems(listOf(q), forecasts))
        assertThat(score).isWithin(0.0001).of(0.08)
    }

    @Test
    fun `brier score skips AMBIGUOUS resolutions`() {
        val questions = listOf(
            resolvedQuestion("q1", Resolution.YES),
            resolvedQuestion("q2", Resolution.AMBIGUOUS),
        )
        val forecasts = mapOf(
            "q1" to listOf(forecast("q1", 0.8)),
            "q2" to listOf(forecast("q2", 0.5)),
        )
        val score = AnalyticsViewModel.computeBrierScore(scoringItems(questions, forecasts))
        // Only q1 counted -> 0.08
        assertThat(score).isWithin(0.0001).of(0.08)
    }

    @Test
    fun `brier score ignores questions with no forecasts`() {
        val questions = listOf(
            resolvedQuestion("q1", Resolution.YES),
            resolvedQuestion("q2", Resolution.NO),
        )
        // Only q1 has forecasts; q2 contributes nothing -> 0.08.
        val forecasts = mapOf("q1" to listOf(forecast("q1", 0.8)))
        val score = AnalyticsViewModel.computeBrierScore(scoringItems(questions, forecasts))
        assertThat(score).isWithin(0.0001).of(0.08)
    }

    @Test
    fun `brier score excludes multiple-choice and quantity questions`() {
        // The MC parent resolves YES whenever an option won, and its forecast
        // rows belong to options — scoring them would corrupt the Brier score.
        val mc = resolvedQuestion("mc1", Resolution.YES).copy(type = QuestionType.MULTIPLE_CHOICE)
        val quantity = resolvedQuestion("qt1", Resolution.YES).copy(type = QuestionType.QUANTITY)
        val forecasts = mapOf(
            "mc1" to listOf(forecast("mc1", 0.9)),
            "qt1" to listOf(forecast("qt1", 0.9)),
        )

        val items = scoringItems(listOf(mc, quantity), forecasts)
        assertThat(AnalyticsViewModel.computeBrierScore(items)).isNull()
        assertThat(AnalyticsViewModel.computeCalibrationBuckets(items)).isEmpty()
        assertThat(AnalyticsViewModel.countScoredForecasts(items)).isEqualTo(0)
    }

    // --- full-history sync ---

    @Test
    fun `init auto-triggers the history sync when never synced`() = runTest {
        every { prefs.fullHistorySynced } returns flowOf(false)
        coEvery { repository.loadAllQuestions(any()) } just Runs

        createViewModel()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.loadAllQuestions(any()) }
        coVerify { prefs.setFullHistorySynced(true) }
    }

    @Test
    fun `init skips the history sync when already synced`() = runTest {
        every { prefs.fullHistorySynced } returns flowOf(true)

        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.loadAllQuestions(any()) }
        assertThat(vm.uiState.value.historySync.isComplete).isTrue()
    }

    @Test
    fun `syncFullHistory reports progress and completion`() = runTest {
        every { prefs.fullHistorySynced } returns flowOf(true)
        coEvery { repository.loadAllQuestions(any()) } coAnswers {
            firstArg<(Int) -> Unit>().invoke(100)
            firstArg<(Int) -> Unit>().invoke(200)
        }

        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        vm.syncFullHistory()
        advanceUntilIdle()

        assertThat(vm.uiState.value.historySync.isComplete).isTrue()
        assertThat(vm.uiState.value.historySync.syncedCount).isEqualTo(200)
        coVerify { prefs.setFullHistorySynced(true) }
    }

    @Test
    fun `syncFullHistory surfaces errors quietly and stays incomplete`() = runTest {
        every { prefs.fullHistorySynced } returns flowOf(false)
        coEvery { repository.loadAllQuestions(any()) } throws RuntimeException("boom")

        val vm = createViewModel() // init auto-triggers and fails
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        assertThat(vm.uiState.value.historySync.isComplete).isFalse()
        assertThat(vm.uiState.value.historySync.error).isEqualTo("boom")
        coVerify(exactly = 0) { prefs.setFullHistorySynced(true) }
    }

    @Test
    fun `tag breakdown groups questions by tag and sorts best first`() {
        // "work": one question at 0.8-YES -> 0.08. "health": one at 0.6-YES -> 0.32.
        // "both" appears on the 0.8 question too.
        val q1 = resolvedQuestion("q1", Resolution.YES).copy(tags = listOf("work", "both"))
        val q2 = resolvedQuestion("q2", Resolution.YES).copy(tags = listOf("health"))
        val untagged = resolvedQuestion("q3", Resolution.YES)
        val forecasts = mapOf(
            "q1" to listOf(forecast("q1", 0.8)),
            "q2" to listOf(forecast("q2", 0.6)),
            "q3" to listOf(forecast("q3", 0.9)),
        )

        val breakdown =
            AnalyticsViewModel.computeTagBreakdown(scoringItems(listOf(q1, q2, untagged), forecasts))

        assertThat(breakdown.map { it.tag }).containsExactly("work", "both", "health").inOrder()
        assertThat(breakdown[0].brier).isWithin(0.0001).of(0.08)
        assertThat(breakdown[2].brier).isWithin(0.0001).of(0.32)
        assertThat(breakdown.map { it.questionCount }).containsExactly(1, 1, 1)
    }

    @Test
    fun `tag breakdown skips tags with no scorable questions`() {
        val ambiguous = resolvedQuestion("q1", Resolution.AMBIGUOUS).copy(tags = listOf("work"))
        val forecasts = mapOf("q1" to listOf(forecast("q1", 0.8)))

        assertThat(
            AnalyticsViewModel.computeTagBreakdown(scoringItems(listOf(ambiguous), forecasts)),
        ).isEmpty()
    }

    // --- multiple-choice option scoring (website parity) ---

    private fun optionForecast(
        questionId: String,
        optionId: String,
        value: Double,
        createdAtMs: Long = base.toEpochMilli(),
    ) = ForecastEntity(
        questionId = questionId,
        forecast = value,
        createdAtEpochMs = createdAtMs,
        optionId = optionId,
    )

    @Test
    fun `resolved MC options are scored as independent binary events`() {
        // Option A: 0.8 held the whole window, resolved YES -> 0.08.
        // Option B: 0.8, resolved NO -> brier(0.8 vs NO) = 2*(0.8)^2 = 1.28.
        // Overall = mean(0.08, 1.28) = 0.68. Option C unresolved -> excluded.
        val mc = TestData.question(
            id = "mc1",
            type = QuestionType.MULTIPLE_CHOICE,
            resolved = false,
            createdAt = base,
            options = listOf(
                TestData.questionOption(
                    id = "optA",
                    resolution = Resolution.YES,
                    resolvedAt = base.plusMillis(10 * dayMs),
                ),
                TestData.questionOption(
                    id = "optB",
                    resolution = Resolution.NO,
                    resolvedAt = base.plusMillis(10 * dayMs),
                ),
                TestData.questionOption(id = "optC", resolution = null),
            ),
        )
        val forecasts = mapOf(
            "mc1" to listOf(
                optionForecast("mc1", "optA", 0.8),
                optionForecast("mc1", "optB", 0.8),
                optionForecast("mc1", "optC", 0.5),
            ),
        )

        val items = AnalyticsViewModel.buildScoringInputs(listOf(mc), forecasts)

        assertThat(items).hasSize(2)
        val score = AnalyticsViewModel.computeBrierScore(items)
        assertThat(score).isWithin(0.0001).of(0.68)
        // Calibration pools the two scored option forecasts.
        assertThat(AnalyticsViewModel.countScoredForecasts(items)).isEqualTo(2)
    }

    @Test
    fun `MC option calibration counts a NO-resolved 80 percent forecast as a miss`() {
        val mc = TestData.question(
            id = "mc1",
            type = QuestionType.MULTIPLE_CHOICE,
            createdAt = base,
            options = listOf(
                TestData.questionOption(
                    id = "optA",
                    resolution = Resolution.NO,
                    resolvedAt = base.plusMillis(dayMs),
                ),
            ),
        )
        val forecasts = mapOf("mc1" to listOf(optionForecast("mc1", "optA", 0.8)))

        val buckets = AnalyticsViewModel.computeCalibrationBuckets(
            AnalyticsViewModel.buildScoringInputs(listOf(mc), forecasts),
        )

        val bucket = buckets.single()
        assertThat(bucket.rangeLabel).isEqualTo("80-85%")
        assertThat(bucket.actualRate).isEqualTo(0f)
    }

    @Test
    fun `binary items ignore stray option-level forecasts`() {
        val q = resolvedQuestion("q1", Resolution.YES)
        val forecasts = mapOf(
            "q1" to listOf(
                forecast("q1", 0.8),
                optionForecast("q1", "ghost", 0.1),
            ),
        )

        val items = AnalyticsViewModel.buildScoringInputs(listOf(q), forecasts)

        assertThat(items.single().rawForecasts).containsExactly(0.8)
    }

    // --- monthly Brier ---

    @Test
    fun `monthly brier buckets by resolution month and keeps the last 12`() {
        val zone = java.time.ZoneOffset.UTC
        // 14 months of questions, one per month, resolved on the 15th.
        val questions = (0 until 14).map { i ->
            resolvedQuestion(
                id = "q$i",
                resolution = Resolution.YES,
                createdAt = Instant.parse("2023-01-01T00:00:00Z").plusMillis(i * 31L * dayMs),
                resolvedAt = Instant.parse("2023-01-15T00:00:00Z").plusMillis(i * 31L * dayMs),
            )
        }
        val forecasts = questions.associate { q ->
            q.id to listOf(forecast(q.id, 0.8, q.createdAt.toEpochMilli()))
        }

        val monthly = AnalyticsViewModel.computeMonthlyBrier(
            AnalyticsViewModel.buildScoringInputs(questions, forecasts),
            zone,
        )

        assertThat(monthly).hasSize(12)
        assertThat(monthly.first().month).isGreaterThan(monthly.first().month.minusMonths(1))
        assertThat(monthly.map { it.month }).isInOrder()
        // 0.8 held from creation to a YES resolution -> 0.08 every month.
        monthly.forEach { assertThat(it.score).isWithin(0.0001).of(0.08) }
        monthly.forEach { assertThat(it.count).isEqualTo(1) }
    }

    @Test
    fun `monthly brier skips months without resolutions`() {
        val questions = listOf(
            resolvedQuestion(
                "q1",
                Resolution.YES,
                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
                resolvedAt = Instant.parse("2024-01-10T00:00:00Z"),
            ),
            resolvedQuestion(
                "q2",
                Resolution.YES,
                createdAt = Instant.parse("2024-03-01T00:00:00Z"),
                resolvedAt = Instant.parse("2024-03-10T00:00:00Z"),
            ),
        )
        val forecasts = questions.associate { q ->
            q.id to listOf(forecast(q.id, 0.9, q.createdAt.toEpochMilli()))
        }

        val monthly = AnalyticsViewModel.computeMonthlyBrier(
            AnalyticsViewModel.buildScoringInputs(questions, forecasts),
            java.time.ZoneOffset.UTC,
        )

        assertThat(monthly.map { it.month.toString() }).containsExactly("2024-01", "2024-03").inOrder()
    }

    @Test
    fun `brier score is null when no forecasts`() {
        val score = AnalyticsViewModel.computeBrierScore(emptyList())
        assertThat(score).isNull()
    }

    // --- Calibration ---

    @Test
    fun `calibration bucketing places forecasts in correct ranges`() {
        val questions = listOf(
            TestData.question(id = "q1", resolved = true, resolution = Resolution.YES),
            TestData.question(id = "q2", resolved = true, resolution = Resolution.NO),
        )
        val forecasts = mapOf(
            "q1" to listOf(forecast("q1", 0.77)),
            // 0.17 folds to 0.83 -> 80-85% bucket
            "q2" to listOf(forecast("q2", 0.17)),
        )
        val buckets = AnalyticsViewModel.computeCalibrationBuckets(scoringItems(questions, forecasts))

        val bucket75 = buckets.find { it.rangeLabel == "75-80%" }
        assertThat(bucket75).isNotNull()
        assertThat(bucket75!!.count).isEqualTo(1)

        val bucket80 = buckets.find { it.rangeLabel == "80-85%" }
        assertThat(bucket80).isNotNull()
        assertThat(bucket80!!.count).isEqualTo(1)
    }

    @Test
    fun `calibration folding flips the outcome for sub-50 forecasts`() {
        // 0.2 on a NO question = correctly predicting "80% it won't happen" -> a hit.
        // 0.2 on a YES question = the unlikely side happened -> a miss.
        val questions = listOf(
            TestData.question(id = "qNo", resolved = true, resolution = Resolution.NO),
            TestData.question(id = "qYes", resolved = true, resolution = Resolution.YES),
        )
        val forecasts = mapOf(
            "qNo" to listOf(forecast("qNo", 0.2)),
            "qYes" to listOf(forecast("qYes", 0.2)),
        )
        val buckets = AnalyticsViewModel.computeCalibrationBuckets(scoringItems(questions, forecasts))

        val bucket80 = buckets.find { it.rangeLabel == "80-85%" }
        assertThat(bucket80).isNotNull()
        assertThat(bucket80!!.count).isEqualTo(2)
        assertThat(bucket80.actualRate).isWithin(0.001f).of(0.5f)
    }

    @Test
    fun `calibration keeps exactly-50 forecasts with original orientation`() {
        val questions = listOf(
            TestData.question(id = "q1", resolved = true, resolution = Resolution.YES),
        )
        val forecasts = mapOf("q1" to listOf(forecast("q1", 0.5)))
        val buckets = AnalyticsViewModel.computeCalibrationBuckets(scoringItems(questions, forecasts))

        val bucket50 = buckets.find { it.rangeLabel == "50-55%" }
        assertThat(bucket50).isNotNull()
        assertThat(bucket50!!.count).isEqualTo(1)
        assertThat(bucket50.actualRate).isWithin(0.001f).of(1f)
    }

    @Test
    fun `calibration folds extreme forecasts into the top bucket`() {
        // 0.0 folds to 1.0 and must land in 95-100% (inclusive top edge).
        val questions = listOf(
            TestData.question(id = "q1", resolved = true, resolution = Resolution.NO),
        )
        val forecasts = mapOf("q1" to listOf(forecast("q1", 0.0)))
        val buckets = AnalyticsViewModel.computeCalibrationBuckets(scoringItems(questions, forecasts))

        val bucket95 = buckets.find { it.rangeLabel == "95-100%" }
        assertThat(bucket95).isNotNull()
        assertThat(bucket95!!.count).isEqualTo(1)
        assertThat(bucket95.actualRate).isWithin(0.001f).of(1f)
    }

    @Test
    fun `calibration counts multiple forecasts per question`() {
        val questions = listOf(
            TestData.question(id = "q1", resolved = true, resolution = Resolution.YES),
        )
        val forecasts = mapOf(
            "q1" to listOf(forecast("q1", 0.76), forecast("q1", 0.77), forecast("q1", 0.78)),
        )
        val buckets = AnalyticsViewModel.computeCalibrationBuckets(scoringItems(questions, forecasts))
        val bucket75 = buckets.find { it.rangeLabel == "75-80%" }
        assertThat(bucket75).isNotNull()
        assertThat(bucket75!!.count).isEqualTo(3)
        assertThat(bucket75.actualRate).isWithin(0.001f).of(1f) // all YES
    }

    @Test
    fun `calibration uses bucket center for predictedRate`() {
        val questions = listOf(
            TestData.question(id = "q1", resolved = true, resolution = Resolution.YES),
        )
        val forecasts = mapOf("q1" to listOf(forecast("q1", 0.77)))
        val buckets = AnalyticsViewModel.computeCalibrationBuckets(scoringItems(questions, forecasts))
        val bucket = buckets.find { it.rangeLabel == "75-80%" }
        assertThat(bucket).isNotNull()
        assertThat(bucket!!.predictedRate).isWithin(0.001f).of(0.775f)
    }

    // --- Streak ---

    @Test
    fun `streak counting - 3 consecutive days`() {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val forecasts = listOf(
            forecast("q1", 0.5, today.atStartOfDay(zone).toInstant().toEpochMilli()),
            forecast("q2", 0.5, today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()),
            forecast("q3", 0.5, today.minusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()),
        )
        val streak = AnalyticsViewModel.computeStreak(forecasts)
        assertThat(streak).isEqualTo(3)
    }

    @Test
    fun `streak resets on gap day`() {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val forecasts = listOf(
            forecast("q1", 0.5, today.atStartOfDay(zone).toInstant().toEpochMilli()),
            // gap: yesterday has no forecast
            forecast("q2", 0.5, today.minusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()),
        )
        val streak = AnalyticsViewModel.computeStreak(forecasts)
        assertThat(streak).isEqualTo(1)
    }

    // --- Daily Activity ---

    @Test
    fun `daily activity counts forecasts per day`() {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val todayMs = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val forecasts = listOf(
            forecast("q1", 0.5, todayMs),
            forecast("q1", 0.6, todayMs),
            forecast("q2", 0.7, todayMs),
        )
        val activity = AnalyticsViewModel.computeDailyActivity(forecasts)
        assertThat(activity).hasSize(77)
        val todayEntry = activity.firstOrNull { it.date == today }
        assertThat(todayEntry).isNotNull()
        assertThat(todayEntry!!.count).isEqualTo(3)
    }

    @Test
    fun `daily activity window ends at the Sunday of the current week`() {
        val activity = AnalyticsViewModel.computeDailyActivity(emptyList())
        val today = LocalDate.now()
        assertThat(activity.last().date.dayOfWeek).isEqualTo(java.time.DayOfWeek.SUNDAY)
        assertThat(activity.last().date).isAtLeast(today)
        assertThat(activity.first().date.dayOfWeek).isEqualTo(java.time.DayOfWeek.MONDAY)
    }

    // --- ViewModel integration ---

    @Test
    fun `init does not eagerly fetch all pages`() = runTest {
        createViewModel()
        advanceUntilIdle()

        // AnalyticsViewModel observes the existing cache; it must NOT trigger
        // a full network paginate-all on construction (was a major source of jank).
        coVerify(exactly = 0) { repository.loadAllQuestions() }
    }

    @Test
    fun `loading state is true initially, false after data`() = runTest {
        val vm = createViewModel()

        assertThat(vm.uiState.value.isLoading).isTrue()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        assertThat(vm.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `empty data shows all zeros and nulls`() = runTest {
        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.brierScore).isNull()
        assertThat(state.totalForecasts).isEqualTo(0)
        assertThat(state.calibrationBuckets).isEmpty()
        assertThat(state.currentStreak).isEqualTo(0)
        assertThat(state.isLoading).isFalse()
    }
}
