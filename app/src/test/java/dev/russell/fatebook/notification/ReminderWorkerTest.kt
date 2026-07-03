package dev.russell.fatebook.notification

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.google.common.truth.Truth.assertThat
import dev.russell.fatebook.data.repository.QuestionRepository
import dev.russell.fatebook.domain.model.QuestionType
import dev.russell.fatebook.testutil.TestData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ReminderWorkerTest {

    private val context = mockk<Context>(relaxed = true)
    private val params = mockk<WorkerParameters>(relaxed = true) {
        every { taskExecutor } returns mockk(relaxed = true)
    }
    private val repository = mockk<QuestionRepository>(relaxed = true)
    private val notificationHelper = mockk<NotificationHelper>(relaxed = true)

    private lateinit var worker: ReminderWorker

    @Before
    fun setup() {
        coEvery { repository.getReadyToResolve() } returns emptyList()
        worker = ReminderWorker(context, params, repository, notificationHelper) { /* no-op widget */ }
    }

    @Test
    fun `doWork always shows daily reminder notification`() = runTest {
        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        coVerify { notificationHelper.showReminderNotification(any()) }
    }

    @Test
    fun `doWork shows resolve notifications when questions are ready`() = runTest {
        val ready = listOf(TestData.question(id = "q1"), TestData.question(id = "q2"))
        coEvery { repository.getReadyToResolve() } returns ready

        worker.doWork()

        coVerify { notificationHelper.showReadyToResolveNotifications(any(), ready) }
    }

    @Test
    fun `doWork does not show resolve notifications when none are ready`() = runTest {
        coEvery { repository.getReadyToResolve() } returns emptyList()

        worker.doWork()

        coVerify(exactly = 0) {
            notificationHelper.showReadyToResolveNotifications(any(), any())
        }
    }

    // --- pure notification fan-out decisions ---

    @Test
    fun `selectForNotifications caps at five, keeping the first (most overdue)`() {
        val questions = (1..8).map { TestData.question(id = "q$it") }

        val selected = NotificationHelper.selectForNotifications(questions)

        assertThat(selected.map { it.id })
            .containsExactly("q1", "q2", "q3", "q4", "q5").inOrder()
    }

    @Test
    fun `only binary questions get YES-NO actions`() {
        assertThat(
            NotificationHelper.hasResolveActions(TestData.question(id = "b")),
        ).isTrue()
        assertThat(
            NotificationHelper.hasResolveActions(
                TestData.question(id = "mc", type = QuestionType.MULTIPLE_CHOICE),
            ),
        ).isFalse()
        assertThat(
            NotificationHelper.hasResolveActions(
                TestData.question(id = "qt", type = QuestionType.QUANTITY),
            ),
        ).isFalse()
    }

    @Test
    fun `notification ids are stable per question and clear of the fixed ids`() {
        val id1 = NotificationHelper.notificationIdFor("question-a")
        val id2 = NotificationHelper.notificationIdFor("question-a")
        val id3 = NotificationHelper.notificationIdFor("question-b")

        assertThat(id1).isEqualTo(id2)
        assertThat(id1).isNotEqualTo(id3)
        assertThat(id1).isGreaterThan(NotificationHelper.RESOLVE_NOTIFICATION_ID)
    }
}
