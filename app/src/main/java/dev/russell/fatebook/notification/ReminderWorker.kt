package dev.russell.fatebook.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.russell.fatebook.data.repository.QuestionRepository

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val questionRepository: QuestionRepository,
    private val notificationHelper: NotificationHelper,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        notificationHelper.showReminderNotification(applicationContext)

        val resolveCount = questionRepository.countReadyToResolve()
        if (resolveCount > 0) {
            notificationHelper.showReadyToResolveNotification(applicationContext, resolveCount)
        }

        return Result.success()
    }
}
