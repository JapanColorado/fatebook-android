package dev.russell.fatebook.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.russell.fatebook.data.repository.QuestionRepository
import dev.russell.fatebook.widget.WidgetRefresher

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val questionRepository: QuestionRepository,
    private val notificationHelper: NotificationHelper,
    private val widgetRefresher: WidgetRefresher,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        notificationHelper.showReminderNotification(applicationContext)

        val readyToResolve = questionRepository.getReadyToResolve()
        if (readyToResolve.isNotEmpty()) {
            notificationHelper.showReadyToResolveNotifications(applicationContext, readyToResolve)
        }
        // Daily tick doubles as a widget freshness pass.
        runCatching { widgetRefresher.refresh() }

        return Result.success()
    }
}
