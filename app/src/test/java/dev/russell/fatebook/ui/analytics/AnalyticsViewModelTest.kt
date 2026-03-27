package dev.russell.fatebook.ui.analytics

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.russell.fatebook.data.repository.QuestionRepository
import dev.russell.fatebook.domain.model.Resolution
import dev.russell.fatebook.testutil.TestData
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

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { repository.observeAll() } returns flowOf(emptyList())
        every { repository.observeResolved() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): AnalyticsViewModel {
        return AnalyticsViewModel(repository)
    }

    @Test
    fun `brier score calculation with known data`() {
        // forecast 0.8, resolved YES -> (0.8 - 1.0)^2 = 0.04
        val questions = listOf(
            TestData.question(
                id = "q1",
                yourLatestForecast = 0.8,
                resolved = true,
                resolution = Resolution.YES,
            ),
        )
        val score = AnalyticsViewModel.computeBrierScore(questions)
        assertThat(score).isWithin(0.0001).of(0.04)
    }

    @Test
    fun `brier score with multiple questions`() {
        // forecast 0.8, YES -> 0.04
        // forecast 0.3, NO -> 0.09
        // average = 0.065
        val questions = listOf(
            TestData.question(
                id = "q1",
                yourLatestForecast = 0.8,
                resolved = true,
                resolution = Resolution.YES,
            ),
            TestData.question(
                id = "q2",
                yourLatestForecast = 0.3,
                resolved = true,
                resolution = Resolution.NO,
            ),
        )
        val score = AnalyticsViewModel.computeBrierScore(questions)
        assertThat(score).isWithin(0.0001).of(0.065)
    }

    @Test
    fun `brier score skips AMBIGUOUS resolutions`() {
        val questions = listOf(
            TestData.question(
                id = "q1",
                yourLatestForecast = 0.8,
                resolved = true,
                resolution = Resolution.YES,
            ),
            TestData.question(
                id = "q2",
                yourLatestForecast = 0.5,
                resolved = true,
                resolution = Resolution.AMBIGUOUS,
            ),
        )
        val score = AnalyticsViewModel.computeBrierScore(questions)
        // Only q1 counted: (0.8 - 1.0)^2 = 0.04
        assertThat(score).isWithin(0.0001).of(0.04)
    }

    @Test
    fun `brier score is null when no resolved questions`() {
        val score = AnalyticsViewModel.computeBrierScore(emptyList())
        assertThat(score).isNull()
    }

    @Test
    fun `calibration bucketing places forecasts in correct ranges`() {
        val questions = listOf(
            TestData.question(
                id = "q1",
                yourLatestForecast = 0.75,
                resolved = true,
                resolution = Resolution.YES,
            ),
            TestData.question(
                id = "q2",
                yourLatestForecast = 0.15,
                resolved = true,
                resolution = Resolution.NO,
            ),
        )
        val buckets = AnalyticsViewModel.computeCalibrationBuckets(questions)

        // q1 (0.75) should be in 70-80% bucket
        val bucket70 = buckets.find { it.rangeLabel == "70-80%" }
        assertThat(bucket70).isNotNull()
        assertThat(bucket70!!.count).isEqualTo(1)

        // q2 (0.15) should be in 10-20% bucket
        val bucket10 = buckets.find { it.rangeLabel == "10-20%" }
        assertThat(bucket10).isNotNull()
        assertThat(bucket10!!.count).isEqualTo(1)
    }

    @Test
    fun `calibration actual rate is correct`() {
        // 2 of 3 resolved YES in 70-80% bucket -> actualRate = 0.667
        val questions = listOf(
            TestData.question(
                id = "q1",
                yourLatestForecast = 0.75,
                resolved = true,
                resolution = Resolution.YES,
            ),
            TestData.question(
                id = "q2",
                yourLatestForecast = 0.72,
                resolved = true,
                resolution = Resolution.YES,
            ),
            TestData.question(
                id = "q3",
                yourLatestForecast = 0.78,
                resolved = true,
                resolution = Resolution.NO,
            ),
        )
        val buckets = AnalyticsViewModel.computeCalibrationBuckets(questions)
        val bucket70 = buckets.find { it.rangeLabel == "70-80%" }
        assertThat(bucket70).isNotNull()
        assertThat(bucket70!!.count).isEqualTo(3)
        assertThat(bucket70.actualRate).isWithin(0.001f).of(2f / 3f)
    }

    @Test
    fun `streak counting - 3 consecutive days`() {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val questions = listOf(
            TestData.question(
                id = "q1",
                latestForecastAt = today.atStartOfDay(zone).toInstant(),
            ),
            TestData.question(
                id = "q2",
                latestForecastAt = today.minusDays(1).atStartOfDay(zone).toInstant(),
            ),
            TestData.question(
                id = "q3",
                latestForecastAt = today.minusDays(2).atStartOfDay(zone).toInstant(),
            ),
        )
        val streak = AnalyticsViewModel.computeStreak(questions)
        assertThat(streak).isEqualTo(3)
    }

    @Test
    fun `streak resets on gap day`() {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val questions = listOf(
            TestData.question(
                id = "q1",
                latestForecastAt = today.atStartOfDay(zone).toInstant(),
            ),
            // gap: yesterday has no forecast
            TestData.question(
                id = "q2",
                latestForecastAt = today.minusDays(2).atStartOfDay(zone).toInstant(),
            ),
        )
        val streak = AnalyticsViewModel.computeStreak(questions)
        assertThat(streak).isEqualTo(1)
    }

    @Test
    fun `weekly activity counts predictions per week`() {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        // Place 3 questions in the current week
        val questions = listOf(
            TestData.question(
                id = "q1",
                latestForecastAt = today.atStartOfDay(zone).toInstant(),
            ),
            TestData.question(
                id = "q2",
                latestForecastAt = today.atStartOfDay(zone).toInstant(),
            ),
            TestData.question(
                id = "q3",
                latestForecastAt = today.atStartOfDay(zone).toInstant(),
            ),
        )
        val activity = AnalyticsViewModel.computeWeeklyActivity(questions)
        assertThat(activity).hasSize(12)
        // The last week (most recent) should contain our 3 questions
        val lastWeekCount = activity.last().count
        assertThat(lastWeekCount).isEqualTo(3)
    }

    @Test
    fun `loading state is true initially, false after data`() = runTest {
        val vm = createViewModel()

        // Initial state should have isLoading = true
        assertThat(vm.uiState.value.isLoading).isTrue()

        // After collecting, should be false
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
        assertThat(state.totalResolved).isEqualTo(0)
        assertThat(state.calibrationBuckets).isEmpty()
        assertThat(state.currentStreak).isEqualTo(0)
        assertThat(state.isLoading).isFalse()
    }
}
