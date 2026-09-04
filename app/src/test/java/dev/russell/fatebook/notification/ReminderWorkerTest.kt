package dev.russell.fatebook.notification

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.google.common.truth.Truth.assertThat
import dev.russell.fatebook.data.preferences.NotificationToggles
import dev.russell.fatebook.data.preferences.UserPreferences
import dev.russell.fatebook.data.repository.QuestionRepository
import dev.russell.fatebook.domain.model.QuestionType
import dev.russell.fatebook.testutil.TestData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
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
    private val prefs = mockk<UserPreferences>(relaxed = true)

    private lateinit var worker: ReminderWorker

    @Before
    fun setup() {
        coEvery { repository.getReadyToResolve() } returns emptyList()
        coEvery { repository.getResolvingSoon(any()) } returns emptyList()
        every { prefs.notificationToggles } returns flowOf(NotificationToggles())
        every { prefs.lastPredictionDateEpochMs } returns flowOf(0L)
        worker = ReminderWorker(context, params, repository, notificationHelper, prefs) { /* no-op widget */ }
    }

    @Test
    fun `doWork shows daily reminder when nothing predicted today`() = runTest {
        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        coVerify { notificationHelper.showReminderNotification(any()) }
    }

    @Test
    fun `doWork skips daily reminder when already predicted today`() = runTest {
        every { prefs.lastPredictionDateEpochMs } returns flowOf(System.currentTimeMillis())

        worker.doWork()

        coVerify(exactly = 0) { notificationHelper.showReminderNotification(any()) }
    }

    @Test
    fun `daily reminder toggle off suppresses the reminder`() = runTest {
        every { prefs.notificationToggles } returns flowOf(NotificationToggles(dailyReminder = false))

        worker.doWork()

        coVerify(exactly = 0) { notificationHelper.showReminderNotification(any()) }
    }

    @Test
    fun `ready-to-resolve toggle off suppresses those notifications`() = runTest {
        every { prefs.notificationToggles } returns flowOf(NotificationToggles(readyToResolve = false))
        coEvery { repository.getReadyToResolve() } returns listOf(TestData.question(id = "q1"))

        worker.doWork()

        coVerify(exactly = 0) { notificationHelper.showReadyToResolveNotifications(any(), any()) }
    }

    @Test
    fun `doWork shows resolving-soon digest when questions are due soon`() = runTest {
        val soon = listOf(TestData.question(id = "s1"))
        coEvery { repository.getResolvingSoon(ReminderPolicy.RESOLVING_SOON_DAYS) } returns soon

        worker.doWork()

        coVerify { notificationHelper.showResolvingSoonNotification(any(), soon) }
    }

    @Test
    fun `resolving-soon toggle off suppresses the digest`() = runTest {
        every { prefs.notificationToggles } returns flowOf(NotificationToggles(resolvingSoon = false))
        coEvery { repository.getResolvingSoon(any()) } returns listOf(TestData.question(id = "s1"))

        worker.doWork()

        coVerify(exactly = 0) { notificationHelper.showResolvingSoonNotification(any(), any()) }
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
    fun `selectForNotifications caps at five, keeping the most overdue regardless of input order`() {
        // Distinct resolve-by dates, q1 most overdue … q8 least; passed in
        // reversed so the cap can only be right if it sorts for itself.
        val byOverdue = (1..8).map {
            TestData.question(
                id = "q$it",
                resolveBy = java.time.Instant.parse("2020-01-0${it}T00:00:00Z"),
            )
        }

        val selected = NotificationHelper.selectForNotifications(byOverdue.reversed())

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
    fun `snooze is offered for everything but QUANTITY`() {
        assertThat(NotificationHelper.hasSnoozeAction(TestData.question(id = "b"))).isTrue()
        assertThat(
            NotificationHelper.hasSnoozeAction(TestData.question(id = "mc", type = QuestionType.MULTIPLE_CHOICE)),
        ).isTrue()
        assertThat(
            NotificationHelper.hasSnoozeAction(TestData.question(id = "qt", type = QuestionType.QUANTITY)),
        ).isFalse()
    }

    @Test
    fun `request codes are unique across actions and questions and clear of the fixed codes`() {
        val actions = listOf(
            NotificationHelper.ACTION_OPEN,
            NotificationHelper.ACTION_YES,
            NotificationHelper.ACTION_NO,
            NotificationHelper.ACTION_SNOOZE,
        )
        val codes = listOf("question-a", "question-b").flatMap { q ->
            actions.map { NotificationHelper.requestCodeFor(q, it) }
        }

        assertThat(codes).containsNoDuplicates()
        assertThat(codes.min()).isGreaterThan(2)
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
