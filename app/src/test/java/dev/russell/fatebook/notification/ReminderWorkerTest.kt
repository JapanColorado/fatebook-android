package dev.russell.fatebook.notification

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.google.common.truth.Truth.assertThat
import dev.russell.fatebook.data.repository.QuestionRepository
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
        coEvery { repository.countReadyToResolve() } returns 0
        worker = ReminderWorker(context, params, repository, notificationHelper)
    }

    @Test
    fun `doWork always shows daily reminder notification`() = runTest {
        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        coVerify { notificationHelper.showReminderNotification(any()) }
    }

    @Test
    fun `doWork shows resolve notification when questions are ready`() = runTest {
        coEvery { repository.countReadyToResolve() } returns 3

        worker.doWork()

        coVerify { notificationHelper.showReadyToResolveNotification(any(), 3) }
    }

    @Test
    fun `doWork does not show resolve notification when count is zero`() = runTest {
        coEvery { repository.countReadyToResolve() } returns 0

        worker.doWork()

        coVerify(exactly = 0) { notificationHelper.showReadyToResolveNotification(any(), any()) }
    }

    @Test
    fun `doWork shows both notifications when questions are ready`() = runTest {
        coEvery { repository.countReadyToResolve() } returns 5

        worker.doWork()

        coVerify { notificationHelper.showReminderNotification(any()) }
        coVerify { notificationHelper.showReadyToResolveNotification(any(), 5) }
    }
}
