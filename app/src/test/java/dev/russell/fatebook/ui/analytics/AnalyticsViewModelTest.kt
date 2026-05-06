package dev.russell.fatebook.ui.analytics

import com.google.common.truth.Truth.assertThat
import dev.russell.fatebook.data.local.ForecastEntity
import dev.russell.fatebook.data.repository.QuestionRepository
import dev.russell.fatebook.domain.model.Resolution
import dev.russell.fatebook.testutil.TestData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
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
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<QuestionRepository>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { repository.observeAll() } returns flowOf(emptyList())
        every { repository.observeResolved() } returns flowOf(emptyList())
        every { repository.observeAllForecasts() } returns flowOf(emptyList())
        coEvery { repository.loadAllQuestions() } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): AnalyticsViewModel {
        return AnalyticsViewModel(repository)
    }

    private fun forecast(questionId: String, value: Double, createdAtMs: Long = 0L) =
        ForecastEntity(questionId = questionId, forecast = value, createdAtEpochMs = createdAtMs)

    // --- Brier Score ---

    @Test
    fun `brier score calculation with known data`() {
        // forecast 0.8, resolved YES -> (0.8 - 1.0)^2 = 0.04
        val questions = listOf(
            TestData.question(id = "q1", resolved = true, resolution = Resolution.YES),
        )
        val forecasts = mapOf("q1" to listOf(forecast("q1", 0.8)))
        val score = AnalyticsViewModel.computeBrierScore(questions, forecasts)
        assertThat(score).isWithin(0.0001).of(0.04)
    }

    @Test
    fun `brier score with multiple forecasts on same question`() {
        // q1: forecasts 0.8 and 0.6, resolved YES
        // (0.8 - 1.0)^2 = 0.04, (0.6 - 1.0)^2 = 0.16
        // average = 0.10
        val questions = listOf(
            TestData.question(id = "q1", resolved = true, resolution = Resolution.YES),
        )
        val forecasts = mapOf("q1" to listOf(forecast("q1", 0.8), forecast("q1", 0.6)))
        val score = AnalyticsViewModel.computeBrierScore(questions, forecasts)
        assertThat(score).isWithin(0.0001).of(0.10)
    }

    @Test
    fun `brier score with multiple questions`() {
        // q1: forecast 0.8, YES -> 0.04
        // q2: forecast 0.3, NO -> 0.09
        // average = 0.065
        val questions = listOf(
            TestData.question(id = "q1", resolved = true, resolution = Resolution.YES),
            TestData.question(id = "q2", resolved = true, resolution = Resolution.NO),
        )
        val forecasts = mapOf(
            "q1" to listOf(forecast("q1", 0.8)),
            "q2" to listOf(forecast("q2", 0.3)),
        )
        val score = AnalyticsViewModel.computeBrierScore(questions, forecasts)
        assertThat(score).isWithin(0.0001).of(0.065)
    }

    @Test
    fun `brier score skips AMBIGUOUS resolutions`() {
        val questions = listOf(
            TestData.question(id = "q1", resolved = true, resolution = Resolution.YES),
            TestData.question(id = "q2", resolved = true, resolution = Resolution.AMBIGUOUS),
        )
        val forecasts = mapOf(
            "q1" to listOf(forecast("q1", 0.8)),
            "q2" to listOf(forecast("q2", 0.5)),
        )
        val score = AnalyticsViewModel.computeBrierScore(questions, forecasts)
        // Only q1 counted: (0.8 - 1.0)^2 = 0.04
        assertThat(score).isWithin(0.0001).of(0.04)
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
    fun `init triggers loadAllQuestions`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        coVerify { repository.loadAllQuestions() }
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
