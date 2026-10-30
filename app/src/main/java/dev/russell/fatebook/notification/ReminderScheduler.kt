package dev.russell.fatebook.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.russell.fatebook.data.preferences.UserPreferences
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Arms one alarm for the next occurrence of the chosen local time. Alarms don't
 * repeat here: [ReminderAlarmReceiver] re-arms on each firing, recomputing from
 * wall-clock time so DST and timezone changes can't shift the reminder.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: UserPreferences,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** False when the user hasn't granted "Alarms & reminders" (default on Android 14+). */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun schedule(hour: Int, minute: Int) {
        val triggerAt = ReminderPolicy.nextOccurrence(ZonedDateTime.now(), hour, minute)
            .toInstant()
            .toEpochMilli()
        val intent = alarmIntent(PendingIntent.FLAG_UPDATE_CURRENT)!!
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        } else {
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, FALLBACK_WINDOW_MS, intent)
        }
    }

    fun cancel() {
        alarmIntent(PendingIntent.FLAG_NO_CREATE)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    /** Make the alarm match the saved settings, replacing any pending one. */
    suspend fun rearm() {
        if (prefs.notificationsEnabled.first()) {
            schedule(prefs.reminderHour.first(), prefs.reminderMinute.first())
        } else {
            cancel()
        }
    }

    /**
     * App-start safety net, for alarms lost to a force-stop or a revoked permission.
     * Leaves a pending alarm alone: a fallback alarm can fire after its trigger time,
     * and re-arming during that window would skip today's. Without exact access the
     * pending check can't be trusted (a revocation drops the alarm but may leave the
     * PendingIntent), so that case always re-arms.
     */
    suspend fun ensureArmed() {
        // Installs from before the move to alarms still have this periodic job.
        WorkManager.getInstance(context).cancelUniqueWork(LEGACY_WORK_NAME)
        if (alarmIntent(PendingIntent.FLAG_NO_CREATE) == null || !canScheduleExact()) rearm()
    }

    private fun alarmIntent(flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ReminderAlarmReceiver::class.java)
            .setAction(ReminderAlarmReceiver.ACTION_FIRE),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val LEGACY_WORK_NAME = "daily_reminder"
        val FALLBACK_WINDOW_MS = TimeUnit.MINUTES.toMillis(10)
    }
}
