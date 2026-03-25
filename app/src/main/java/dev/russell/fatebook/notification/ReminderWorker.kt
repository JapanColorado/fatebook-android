package dev.russell.fatebook.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.russell.fatebook.data.preferences.UserPreferences
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val userPreferences: UserPreferences,
    private val notificationHelper: NotificationHelper,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val lastPredictionMs = userPreferences.lastPredictionDateEpochMs.first()
        val hasPredictedToday = isPredictionFromToday(lastPredictionMs)

        if (!hasPredictedToday) {
            notificationHelper.showReminderNotification(applicationContext)
        }

        return Result.success()
    }

    private fun isPredictionFromToday(epochMs: Long): Boolean {
        if (epochMs == 0L) return false
        val predictionDate = Instant.ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        return predictionDate == LocalDate.now()
    }
}
