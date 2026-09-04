package dev.russell.fatebook.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.russell.fatebook.data.preferences.UserPreferences
import dev.russell.fatebook.data.repository.QuestionRepository
import dev.russell.fatebook.widget.WidgetRefresher
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime

/**
 * The daily tick. Fires up to three notification kinds, each behind its own
 * toggle in Settings:
 *  - the "time to predict" nudge, skipped on days you've already predicted in-app
 *  - per-question ready-to-resolve notifications (with YES/NO and +1 week actions)
 *  - one "resolving soon" digest for questions due in the next few days
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val questionRepository: QuestionRepository,
    private val notificationHelper: NotificationHelper,
    private val prefs: UserPreferences,
    private val widgetRefresher: WidgetRefresher,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val toggles = prefs.notificationToggles.first()

        if (toggles.dailyReminder) {
            val lastPrediction = prefs.lastPredictionDateEpochMs.first()
            if (!ReminderPolicy.predictedToday(lastPrediction, ZonedDateTime.now())) {
                notificationHelper.showReminderNotification(applicationContext)
            }
        }

        if (toggles.readyToResolve) {
            val readyToResolve = questionRepository.getReadyToResolve()
            if (readyToResolve.isNotEmpty()) {
                notificationHelper.showReadyToResolveNotifications(applicationContext, readyToResolve)
            }
        }

        if (toggles.resolvingSoon) {
            val soon = questionRepository.getResolvingSoon(ReminderPolicy.RESOLVING_SOON_DAYS)
            if (soon.isNotEmpty()) {
                notificationHelper.showResolvingSoonNotification(applicationContext, soon)
            }
        }

        // Daily tick doubles as a widget freshness pass.
        runCatching { widgetRefresher.refresh() }

        return Result.success()
    }
}
