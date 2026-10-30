package dev.russell.fatebook.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives the daily alarm, plus the system broadcasts after which it must be
 * re-armed (reboot, app update, clock or timezone change, exact-alarm grant).
 * Non-exported system broadcasts still arrive, and nothing else can fire it.
 */
@AndroidEntryPoint
class ReminderAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduler: ReminderScheduler

    @Inject
    lateinit var tick: ReminderTick

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Re-arm first, so a failing tick can't lose tomorrow's alarm.
                scheduler.rearm()
                if (intent.action == ACTION_FIRE) tick.run()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "dev.russell.fatebook.action.DAILY_REMINDER"
    }
}
