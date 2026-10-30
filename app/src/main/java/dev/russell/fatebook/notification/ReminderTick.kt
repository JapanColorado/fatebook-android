package dev.russell.fatebook.notification

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.russell.fatebook.data.preferences.UserPreferences
import dev.russell.fatebook.data.repository.QuestionRepository
import dev.russell.fatebook.widget.WidgetRefresher
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * The daily tick. Fires up to three notification kinds, each behind its own
 * toggle in Settings:
 *  - the "time to predict" nudge, skipped on days you've already predicted in-app
 *  - per-question ready-to-resolve notifications (with YES/NO and +1 week actions)
 *  - one "resolving soon" digest for questions due in the next few days
 *
 * Reads only the local cache, so [ReminderAlarmReceiver] runs it directly: going
 * through WorkManager would hand the timing back to Doze and app standby.
 */
class ReminderTick @Inject constructor(
    @ApplicationContext private val context: Context,
    private val questionRepository: QuestionRepository,
    private val notificationHelper: NotificationHelper,
    private val prefs: UserPreferences,
    private val widgetRefresher: WidgetRefresher,
) {

    suspend fun run() {
        val toggles = prefs.notificationToggles.first()

        if (toggles.dailyReminder) {
            val lastPrediction = prefs.lastPredictionDateEpochMs.first()
            if (!ReminderPolicy.predictedToday(lastPrediction, ZonedDateTime.now())) {
                notificationHelper.showReminderNotification(context)
            }
        }

        if (toggles.readyToResolve) {
            val readyToResolve = questionRepository.getReadyToResolve()
            if (readyToResolve.isNotEmpty()) {
                notificationHelper.showReadyToResolveNotifications(context, readyToResolve)
            }
        }

        if (toggles.resolvingSoon) {
            val soon = questionRepository.getResolvingSoon(ReminderPolicy.RESOLVING_SOON_DAYS)
            if (soon.isNotEmpty()) {
                notificationHelper.showResolvingSoonNotification(context, soon)
            }
        }

        // Daily tick doubles as a widget freshness pass.
        runCatching { widgetRefresher.refresh() }
    }
}
