package dev.russell.fatebook.data.sync

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Indirection over [SyncWorker.enqueue] so the repository can request a sync
 * without depending on WorkManager directly. Tests substitute a no-op or
 * recording fake; production schedules a one-time worker.
 */
fun interface SyncScheduler {
    fun schedule()
}

@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncScheduler {
    override fun schedule() {
        SyncWorker.enqueue(context)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object SyncSchedulerModule {
    @Provides
    @Singleton
    fun provideSyncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler = impl
}
