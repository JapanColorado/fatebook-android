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
        // Default: getQuestion returns the same question passed to showDetailSheet
        coEvery { repository.getQuestion(any()) } answers {
            TestData.question(id = firstArg())
        }
        coEvery { repository.getCachedQuestion(any()) } returns null
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
    fun `refresh sets Other error on RuntimeException`() = runTest {
        coEvery { repository.refresh() } throws RuntimeException("Server error")

        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        val error = vm.uiState.value.error
        assertThat(error).isInstanceOf(FeedError.Other::class.java)
        assertThat(error?.message).isEqualTo("Server error")
    }

    @Test
    fun `refresh sets Network error on IOException`() = runTest {
        coEvery { repository.refresh() } throws java.io.IOException("No internet")

        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        val error = vm.uiState.value.error
        assertThat(error).isInstanceOf(FeedError.Network::class.java)
        assertThat(error?.message).isEqualTo("No internet")
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
        coEvery { repository.getQuestion(question.id) } returns question

        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        vm.showDetailSheet(question)
        advanceUntilIdle()

        assertThat(vm.uiState.value.detail.question?.id).isEqualTo(question.id)
        assertThat(vm.uiState.value.detail.forecastSliderValue).isEqualTo(0.8f)
    }

    @Test
    fun `showDetailSheet defaults slider to 0_5 when no forecast`() = runTest {
        val question = TestData.question(yourLatestForecast = null)
        coEvery { repository.getQuestion(question.id) } returns question

        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        vm.showDetailSheet(question)
        advanceUntilIdle()

        assertThat(vm.uiState.value.detail.forecastSliderValue).isEqualTo(0.5f)
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

        assertThat(vm.uiState.value.detail.question).isNull()
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
        assertThat(vm.uiState.value.detail.question).isNull()
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

        val error = vm.uiState.value.error
        assertThat(error).isInstanceOf(FeedError.Other::class.java)
        assertThat(error?.message).isEqualTo("Forbidden")
    }

    @Test
    fun `resolveQuestion calls repository and closes sheet`() = runTest {
        val question = TestData.question(id = "q1")

        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        vm.showDetailSheet(question)
        advanceUntilIdle()
        vm.resolveQuestion(Resolution.YES)
        advanceUntilIdle()

        coVerify { repository.resolveQuestion("q1", Resolution.YES) }
        assertThat(vm.uiState.value.detail.question).isNull()
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

        vm.showDetailSheet(question)
        advanceUntilIdle()
        vm.resolveQuestion(Resolution.NO)
        advanceUntilIdle()

        val error = vm.uiState.value.error
        assertThat(error).isInstanceOf(FeedError.Other::class.java)
        assertThat(error?.message).isEqualTo("Failed")
    }

    @Test
    fun `dismissError clears the error`() = runTest {
        coEvery { repository.refresh() } throws RuntimeException("Error")

        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        assertThat(vm.uiState.value.error).isNotNull()

        vm.dismissError()
        advanceUntilIdle()

        assertThat(vm.uiState.value.error).isNull()
    }

    // --- New feature tests ---

    @Test
    fun `confirmDelete calls repository and closes sheet`() = runTest {
        val question = TestData.question(id = "q1")
        coEvery { repository.getQuestion("q1") } returns question

        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        vm.showDetailSheet(question)
        advanceUntilIdle()
        vm.confirmDelete()
        advanceUntilIdle()

        coVerify { repository.deleteQuestion("q1") }
        assertThat(vm.uiState.value.detail.question).isNull()
    }

    @Test
    fun `confirmDelete sets error on failure`() = runTest {
        val question = TestData.question(id = "q1")
        coEvery { repository.getQuestion("q1") } returns question
        coEvery { repository.deleteQuestion(any()) } throws RuntimeException("Forbidden")

        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        vm.showDetailSheet(question)
        advanceUntilIdle()
        vm.confirmDelete()
        advanceUntilIdle()

        assertThat(vm.uiState.value.error).isNotNull()
        assertThat(vm.uiState.value.detail.isDeleting).isFalse()
    }

    @Test
    fun `requestDelete shows confirmation dialog`() = runTest {
        val question = TestData.question(id = "q1")
        coEvery { repository.getQuestion("q1") } returns question

        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        vm.showDetailSheet(question)
        advanceUntilIdle()
        vm.requestDelete()
        advanceUntilIdle()

        assertThat(vm.uiState.value.detail.showDeleteConfirmation).isTrue()
    }

    @Test
    fun `enterEditMode populates edit fields`() = runTest {
        val question = TestData.question(id = "q1", title = "Test question")
        coEvery { repository.getQuestion("q1") } returns question

        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        vm.showDetailSheet(question)
        advanceUntilIdle()
        vm.enterEditMode()
        advanceUntilIdle()

        assertThat(vm.uiState.value.detail.isEditing).isTrue()
        assertThat(vm.uiState.value.detail.editTitle).isEqualTo("Test question")
    }

    @Test
    fun `saveEdit calls repository and closes sheet`() = runTest {
        val question = TestData.question(id = "q1", title = "Old title")
        coEvery { repository.getQuestion("q1") } returns question

        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        vm.showDetailSheet(question)
        advanceUntilIdle()
        vm.enterEditMode()
        vm.setEditTitle("New title")
        vm.saveEdit()
        advanceUntilIdle()

        coVerify { repository.editQuestion(questionId = "q1", title = "New title", resolveBy = any(), notes = any()) }
        assertThat(vm.uiState.value.detail.question).isNull()
    }

    @Test
    fun `addComment calls repository and clears text`() = runTest {
        val question = TestData.question(id = "q1")
        coEvery { repository.getQuestion("q1") } returns question
        coEvery { repository.addComment(any(), any()) } returns question

        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        vm.showDetailSheet(question)
        advanceUntilIdle()
        vm.setCommentText("Great prediction!")
        vm.addComment()
        advanceUntilIdle()

        coVerify { repository.addComment(eq(question), eq("Great prediction!")) }
        assertThat(vm.uiState.value.detail.commentText).isEmpty()
    }

    @Test
    fun `openDeepLinkedQuestion fetches and shows detail`() = runTest {
        val question = TestData.question(id = "deep-q1")
        coEvery { repository.getQuestion("deep-q1") } returns question

        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        vm.openDeepLinkedQuestion("deep-q1")
        advanceUntilIdle()

        assertThat(vm.uiState.value.detail.question?.id).isEqualTo("deep-q1")
    }

    @Test
    fun `toggleSharedPublicly calls repository`() = runTest {
        val question = TestData.question(id = "q1")
        coEvery { repository.getQuestion("q1") } returns question

        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceUntilIdle()

        vm.showDetailSheet(question)
        advanceUntilIdle()
        vm.toggleSharedPublicly()
        advanceUntilIdle()

        coVerify { repository.setSharedPublicly("q1", true, false) }
    }
}
