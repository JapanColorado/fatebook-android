package dev.russell.fatebook.ui.feed

import com.google.common.truth.Truth.assertThat
import dev.russell.fatebook.data.network.NetworkMonitor
import dev.russell.fatebook.data.preferences.UserPreferences
import dev.russell.fatebook.data.repository.QuestionRepository
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** Resolutions can't be undone through the public API, so nothing may be written early. */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedQuickActionTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<QuestionRepository>(relaxed = true)
    private val networkMonitor = mockk<NetworkMonitor>(relaxed = true)
    private val prefs = mockk<UserPreferences>(relaxed = true)
    private val q1 = TestData.question(id = "q1")
    private val q2 = TestData.question(id = "q2")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { repository.observeActive() } returns flowOf(listOf(q1, q2))
        every { repository.observeResolved() } returns flowOf(emptyList())
        every { repository.observeReadyToResolve() } returns flowOf(listOf(q1))
        every { repository.observeResolvingSoon(any()) } returns flowOf(emptyList())
        every { repository.observeAllTags() } returns flowOf(emptyList())
        every { repository.observeCommentSearchIndex() } returns flowOf(emptyMap())
        every { repository.observeErroredMutations() } returns flowOf(emptyList())
        coEvery { repository.refresh() } just Runs
        every { repository.hasMore() } returns false
        every { networkMonitor.isOnline } returns MutableStateFlow(true)
        every { prefs.feedSort } returns flowOf("RESOLVE_BY")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = FeedViewModel(repository, networkMonitor, prefs, testDispatcher)

    private val yes = QuickAction.Resolve(Resolution.YES)

    @Test
    fun `nothing is written during the undo window`() = runTest {
        val vm = viewModel()
        vm.quickAction(q1, yes)
        advanceTimeBy(4_999)
        coVerify(exactly = 0) { repository.resolveQuestion(any(), any()) }
    }

    @Test
    fun `resolve commits once the window closes`() = runTest {
        val vm = viewModel()
        vm.quickAction(q1, yes)
        advanceTimeBy(5_001)
        coVerify(exactly = 1) { repository.resolveQuestion("q1", Resolution.YES) }
    }

    @Test
    fun `undo means the write never happens`() = runTest {
        val vm = viewModel()
        vm.quickAction(q1, yes)
        advanceTimeBy(1_000)
        vm.undoQuickAction()
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.resolveQuestion(any(), any()) }
    }

    @Test
    fun `flush commits immediately and only once`() = runTest {
        val vm = viewModel()
        vm.quickAction(q1, yes)
        vm.flushQuickAction()
        testDispatcher.scheduler.runCurrent()
        coVerify(exactly = 1) { repository.resolveQuestion("q1", Resolution.YES) }
        vm.flushQuickAction()
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.resolveQuestion("q1", Resolution.YES) }
    }

    @Test
    fun `a second action commits the first rather than dropping it`() = runTest {
        val vm = viewModel()
        vm.quickAction(q1, yes)
        vm.quickAction(q2, QuickAction.Resolve(Resolution.NO))
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.resolveQuestion("q1", Resolution.YES) }
        coVerify(exactly = 1) { repository.resolveQuestion("q2", Resolution.NO) }
    }

    @Test
    fun `snooze pushes from today, since an overdue date plus a week may still be past`() = runTest {
        val vm = viewModel()
        vm.quickAction(q1, QuickAction.SnoozeWeek)
        advanceUntilIdle()
        coVerify {
            repository.editQuestion(
                questionId = "q1",
                title = null,
                resolveBy = LocalDate.now().plusWeeks(1),
                notes = null,
            )
        }
    }

    @Test
    fun `pending row is hidden from the list and returns on undo`() = runTest {
        val vm = viewModel()
        val collector = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertThat(vm.uiState.value.questions.map { it.id }).containsExactly("q1", "q2")

        vm.quickAction(q1, yes)
        testDispatcher.scheduler.runCurrent()
        assertThat(vm.uiState.value.questions.map { it.id }).containsExactly("q2")
        assertThat(vm.uiState.value.pendingQuickAction?.question?.id).isEqualTo("q1")

        vm.undoQuickAction()
        testDispatcher.scheduler.runCurrent()
        assertThat(vm.uiState.value.questions.map { it.id }).containsExactly("q1", "q2")
        assertThat(vm.uiState.value.pendingQuickAction).isNull()
        collector.cancel()
    }
}
