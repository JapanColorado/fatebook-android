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
import javax.inject.Inject

/**
 * Handles YES/NO action taps on ready-to-resolve notifications.
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
        val resolution = Resolution.fromApi(intent.getStringExtra(EXTRA_RESOLUTION) ?: "") ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.resolveQuestion(questionId, resolution)
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
        const val EXTRA_QUESTION_ID = "question_id"
        const val EXTRA_RESOLUTION = "resolution"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
