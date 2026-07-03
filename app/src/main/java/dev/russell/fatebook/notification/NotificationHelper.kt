package dev.russell.fatebook.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.russell.fatebook.FatebookApplication
import dev.russell.fatebook.MainActivity
import dev.russell.fatebook.R
import dev.russell.fatebook.domain.model.Question
import dev.russell.fatebook.domain.model.QuestionType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor() {

    fun showReminderNotification(context: Context) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_OPEN_CREATE, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, FatebookApplication.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Time to predict!")
            .setContentText("Make a prediction today! Tap to create one.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /**
     * One notification per ready-to-resolve question (capped, see
     * [selectForNotifications]) grouped under a summary. Binary questions get
     * YES/NO actions that resolve straight through the offline mutation queue;
     * multiple-choice/quantity questions just open the app.
     */
    fun showReadyToResolveNotifications(context: Context, questions: List<Question>) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (questions.isEmpty()) return

        val manager = NotificationManagerCompat.from(context)

        for (question in questions.let(::selectForNotifications)) {
            manager.notify(
                notificationIdFor(question.id),
                buildQuestionNotification(context, question),
            )
        }

        // Group summary carries the total count and the open-filter intent.
        val summaryIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_OPEN_RESOLVE_FILTER, true)
        }
        val summaryPending = PendingIntent.getActivity(
            context, 1, summaryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val count = questions.size
        val text = if (count == 1) {
            "You have 1 question ready to resolve."
        } else {
            "You have $count questions ready to resolve."
        }
        val summary = NotificationCompat.Builder(context, FatebookApplication.RESOLVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Questions ready to resolve")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(summaryPending)
            .setGroup(GROUP_READY_TO_RESOLVE)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        manager.notify(RESOLVE_NOTIFICATION_ID, summary)
    }

    private fun buildQuestionNotification(
        context: Context,
        question: Question,
    ): android.app.Notification {
        val notificationId = notificationIdFor(question.id)

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_QUESTION_ID, question.id)
        }
        val contentPending = PendingIntent.getActivity(
            context,
            requestCodeFor(question.id, action = 0),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, FatebookApplication.RESOLVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Ready to resolve")
            .setContentText(question.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(question.title))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentPending)
            .setGroup(GROUP_READY_TO_RESOLVE)
            .setAutoCancel(true)

        if (hasResolveActions(question)) {
            builder
                .addAction(0, "YES", resolveActionIntent(context, question.id, "YES", notificationId))
                .addAction(0, "NO", resolveActionIntent(context, question.id, "NO", notificationId))
        }
        return builder.build()
    }

    private fun resolveActionIntent(
        context: Context,
        questionId: String,
        resolution: String,
        notificationId: Int,
    ): PendingIntent {
        val intent = Intent(context, ResolveActionReceiver::class.java).apply {
            putExtra(ResolveActionReceiver.EXTRA_QUESTION_ID, questionId)
            putExtra(ResolveActionReceiver.EXTRA_RESOLUTION, resolution)
            putExtra(ResolveActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(questionId, action = if (resolution == "YES") 1 else 2),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val NOTIFICATION_ID = 1
        const val RESOLVE_NOTIFICATION_ID = 2
        const val EXTRA_OPEN_CREATE = "open_create"
        const val EXTRA_OPEN_RESOLVE_FILTER = "open_resolve_filter"
        const val EXTRA_QUESTION_ID = "open_question_id"
        const val GROUP_READY_TO_RESOLVE = "ready_to_resolve_group"

        /** At most this many per-question notifications; the summary carries the rest. */
        const val MAX_QUESTION_NOTIFICATIONS = 5

        private const val RESOLVE_ID_BASE = 10_000

        /**
         * Which questions get their own notification: the DAO already orders
         * ready-to-resolve by resolve-by ascending, so the cap keeps the most
         * overdue ones.
         */
        fun selectForNotifications(questions: List<Question>): List<Question> =
            questions.take(MAX_QUESTION_NOTIFICATIONS)

        /** Only binary questions can offer one-tap YES/NO. */
        fun hasResolveActions(question: Question): Boolean =
            question.type == QuestionType.BINARY

        /** Stable per-question notification id, clear of the fixed ids 1 and 2. */
        fun notificationIdFor(questionId: String): Int =
            RESOLVE_ID_BASE + (questionId.hashCode() and 0x7FFFFFFF) % 100_000

        /**
         * PendingIntent request codes must not collide across questions or with
         * the daily-reminder codes 0/1; action ∈ {0 content, 1 yes, 2 no}.
         */
        private fun requestCodeFor(questionId: String, action: Int): Int =
            1000 + ((questionId.hashCode() and 0x7FFFFFFF) % 100_000) * 3 + action
    }
}
