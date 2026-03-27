package dev.russell.fatebook.ui.feed

import com.google.common.truth.Truth.assertThat
import dev.russell.fatebook.data.repository.QuestionRepository
import dev.russell.fatebook.domain.model.Resolution
import dev.russell.fatebook.testutil.TestData
import io.mockk.coEvery
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

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<QuestionRepository>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { repository.observeActive() } returns flowOf(emptyList())
        coEvery { repository.observeResolved() } returns flowOf(emptyList())
        coEvery { repository.observeReadyToResolve() } returns flowOf(emptyList())
        coEvery { repository.refresh() } returns emptyList()
        every { repository.hasMore() } returns false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): FeedViewModel {
        return FeedViewModel(repository)
    }

    @Test
    fun `init calls refresh`() = runTest {
        createViewModel()
        advanceUntilIdle()

        coVerify { repository.refresh() }
    }

    @Test
    fun `init sets isInitialLoad to false after refresh`() = runTest {
        val vm = createViewModel()
        // Keep a subscriber on uiState so WhileSubscribed starts the upstream
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        assertThat(vm.uiState.value.isInitialLoad).isFalse()
    }

    @Test
    fun `setFilter switches to resolved flow`() = runTest {
        val resolvedQuestion = TestData.question(id = "r1", resolved = true, resolution = Resolution.YES)
        coEvery { repository.observeResolved() } returns flowOf(listOf(resolvedQuestion))

        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        vm.setFilter(FeedFilter.RESOLVED)
        advanceUntilIdle()

        assertThat(vm.uiState.value.filter).isEqualTo(FeedFilter.RESOLVED)
        assertThat(vm.uiState.value.questions).hasSize(1)
        assertThat(vm.uiState.value.questions[0].id).isEqualTo("r1")
    }

    @Test
    fun `setSearchQuery filters questions by title`() = runTest {
        val q1 = TestData.question(id = "q1", title = "Will it rain?")
        val q2 = TestData.question(id = "q2", title = "Will GPT-5 launch?")
        coEvery { repository.observeActive() } returns flowOf(listOf(q1, q2))

        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        vm.setSearchQuery("rain")
        advanceUntilIdle()

        assertThat(vm.uiState.value.questions).hasSize(1)
        assertThat(vm.uiState.value.questions[0].id).isEqualTo("q1")
    }

    @Test
    fun `refresh sets error on failure`() = runTest {
        coEvery { repository.refresh() } throws RuntimeException("Network error")

        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        assertThat(vm.uiState.value.error).isEqualTo("Network error")
    }

    @Test
    fun `refresh clears error on retry`() = runTest {
        coEvery { repository.refresh() } throws RuntimeException("Error")

        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        // Now make refresh succeed
        coEvery { repository.refresh() } returns emptyList()
        vm.refresh()
        advanceUntilIdle()

        assertThat(vm.uiState.value.error).isNull()
    }

    @Test
    fun `loadMore updates hasMore from repository`() = runTest {
        every { repository.hasMore() } returns true
        coEvery { repository.refresh() } returns emptyList()
        coEvery { repository.loadMore() } returns false // no more pages

        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()

        assertThat(vm.uiState.value.hasMore).isFalse()
        assertThat(vm.uiState.value.isLoadingMore).isFalse()
    }

    @Test
    fun `loadMore is noop when hasMore is false`() = runTest {
        every { repository.hasMore() } returns false

        val vm = createViewModel()
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.loadMore() }
    }

    @Test
    fun `showDetailSheet sets target and initializes slider`() = runTest {
        val question = TestData.question(yourLatestForecast = 0.8)

        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        vm.showDetailSheet(question)
        advanceUntilIdle()

        assertThat(vm.uiState.value.detailTarget).isEqualTo(question)
        assertThat(vm.uiState.value.forecastSliderValue).isEqualTo(0.8f)
    }

    @Test
    fun `showDetailSheet defaults slider to 0_5 when no forecast`() = runTest {
        val question = TestData.question(yourLatestForecast = null)

        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        vm.showDetailSheet(question)
        advanceUntilIdle()

        assertThat(vm.uiState.value.forecastSliderValue).isEqualTo(0.5f)
    }

    @Test
    fun `dismissDetailSheet clears target`() = runTest {
        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        vm.showDetailSheet(TestData.question())
        advanceUntilIdle()
        vm.dismissDetailSheet()
        advanceUntilIdle()

        assertThat(vm.uiState.value.detailTarget).isNull()
    }

    @Test
    fun `updateForecast calls repository and closes sheet`() = runTest {
        val question = TestData.question(id = "q1")

        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        vm.showDetailSheet(question)
        vm.setForecastSliderValue(0.9f)
        vm.updateForecast()
        advanceUntilIdle()

        coVerify { repository.addForecast("q1", any()) }
        assertThat(vm.uiState.value.detailTarget).isNull()
    }

    @Test
    fun `updateForecast sets error on failure`() = runTest {
        val question = TestData.question(id = "q1")
        coEvery { repository.addForecast(any(), any()) } throws RuntimeException("Forbidden")

        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        vm.showDetailSheet(question)
        vm.updateForecast()
        advanceUntilIdle()

        assertThat(vm.uiState.value.error).isEqualTo("Forbidden")
    }

    @Test
    fun `resolveQuestion calls repository and closes sheet`() = runTest {
        val question = TestData.question(id = "q1")

        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        vm.showResolveSheet(question)
        vm.resolveQuestion(Resolution.YES)
        advanceUntilIdle()

        coVerify { repository.resolveQuestion("q1", Resolution.YES) }
        assertThat(vm.uiState.value.resolveTarget).isNull()
    }

    @Test
    fun `resolveQuestion sets error on failure`() = runTest {
        val question = TestData.question(id = "q1")
        coEvery { repository.resolveQuestion(any(), any()) } throws RuntimeException("Failed")

        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        vm.showResolveSheet(question)
        vm.resolveQuestion(Resolution.NO)
        advanceUntilIdle()

        assertThat(vm.uiState.value.error).isEqualTo("Failed")
    }
}
