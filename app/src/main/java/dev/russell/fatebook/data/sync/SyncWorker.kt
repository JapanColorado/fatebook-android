package dev.russell.fatebook.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager wrapper around [SyncRunner]. Triggered after every enqueue and on
 * app start; the [Constraints] make sure it only runs when the device has
 * connectivity, so the inner runner usually only sees real network blips.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val runner: SyncRunner,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = when (runner.run()) {
        SyncRunner.Outcome.SUCCESS -> Result.success()
        SyncRunner.Outcome.RETRY -> Result.retry()
    }

    companion object {
        const val WORK_NAME = "fatebook_sync"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
