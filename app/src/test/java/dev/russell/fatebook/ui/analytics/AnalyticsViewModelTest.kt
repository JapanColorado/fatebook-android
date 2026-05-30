package dev.russell.fatebook.ui.analytics

import com.google.common.truth.Truth.assertThat
import dev.russell.fatebook.data.local.ForecastEntity
import dev.russell.fatebook.data.repository.QuestionRepository
import dev.russell.fatebook.domain.model.Resolution
import dev.russell.fatebook.testutil.TestData
import io.mockk.coVerify
import io.mockk.every
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

    private val base: Instant = Instant.parse("2024-01-01T00:00:00Z")
    private val dayMs = 1000L * 60 * 60 * 24

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { repository.observeAll() } returns flowOf(emptyList())
        every { repository.observeResolved() } returns flowOf(emptyList())
        every { repository.observeAllForecasts() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): AnalyticsViewModel {
        return AnalyticsViewModel(repository)
    }

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
        val score = AnalyticsViewModel.computeBrierScore(questions, forecasts)
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
        val score = AnalyticsViewModel.computeBrierScore(questions, forecasts)
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
        val score = AnalyticsViewModel.computeBrierScore(questions, forecasts)
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
        val score = AnalyticsViewModel.computeBrierScore(listOf(q), forecasts)
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
        val score = AnalyticsViewModel.computeBrierScore(questions, forecasts)
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
        val score = AnalyticsViewModel.computeBrierScore(questions, forecasts)
        assertThat(score).isWithin(0.0001).of(0.08)
    }

    @Test
    fun `brier score is null when no forecasts`() {
        val score = AnalyticsViewModel.computeBrierScore(emptyList(), emptyMap())
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
            "q2" to listOf(forecast("q2", 0.17)),
        )
        val buckets = AnalyticsViewModel.computeCalibrationBuckets(questions, forecasts)

        val bucket75 = buckets.find { it.rangeLabel == "75-80%" }
        assertThat(bucket75).isNotNull()
        assertThat(bucket75!!.count).isEqualTo(1)

        val bucket15 = buckets.find { it.rangeLabel == "15-20%" }
        assertThat(bucket15).isNotNull()
        assertThat(bucket15!!.count).isEqualTo(1)
    }

    @Test
    fun `calibration counts multiple forecasts per question`() {
        val questions = listOf(
            TestData.question(id = "q1", resolved = true, resolution = Resolution.YES),
        )
        val forecasts = mapOf(
            "q1" to listOf(forecast("q1", 0.76), forecast("q1", 0.77), forecast("q1", 0.78)),
        )
        val buckets = AnalyticsViewModel.computeCalibrationBuckets(questions, forecasts)
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
        val buckets = AnalyticsViewModel.computeCalibrationBuckets(questions, forecasts)
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
