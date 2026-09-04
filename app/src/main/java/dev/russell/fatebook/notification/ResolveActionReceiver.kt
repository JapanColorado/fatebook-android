package dev.russell.fatebook.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.russell.fatebook.data.repository.QuestionRepository
import dev.russell.fatebook.domain.model.Resolution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Handles YES/NO and "+1 week" action taps on ready-to-resolve notifications.
 *
 * The resolve itself is a local Room transaction plus a queued mutation
 * ([QuestionRepository.resolveQuestion]) — milliseconds of work, well inside
 * the goAsync() budget — and the network sync is owned by the existing
 * SyncWorker, so no Worker indirection is needed here. Works with the app
 * process dead.
 */
@AndroidEntryPoint
class ResolveActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: QuestionRepository

    override fun onReceive(context: Context, intent: Intent) {
        val questionId = intent.getStringExtra(EXTRA_QUESTION_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val snooze = intent.action == ACTION_SNOOZE
        val resolution = if (snooze) {
            null
        } else {
            Resolution.fromApi(intent.getStringExtra(EXTRA_RESOLUTION) ?: "") ?: return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (resolution != null) {
                    repository.resolveQuestion(questionId, resolution)
                } else {
                    // Same mutation as the detail sheet's "+1 week": push from
                    // today, since an overdue date plus a week may still be past.
                    repository.editQuestion(
                        questionId = questionId,
                        resolveBy = LocalDate.now().plusWeeks(1),
                    )
                }
                val manager = NotificationManagerCompat.from(context)
                manager.cancel(notificationId)
                // Last one resolved? Drop the group summary too.
                if (repository.countReadyToResolve() == 0) {
                    manager.cancel(NotificationHelper.RESOLVE_NOTIFICATION_ID)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "dev.russell.fatebook.action.SNOOZE_WEEK"
        const val EXTRA_QUESTION_ID = "question_id"
        const val EXTRA_RESOLUTION = "resolution"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
