package dev.russell.fatebook

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FatebookApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val reminderChannel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        val resolveChannel = NotificationChannel(
            RESOLVE_CHANNEL_ID,
            getString(R.string.resolve_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(R.string.resolve_channel_description)
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannels(listOf(reminderChannel, resolveChannel))
    }

    companion object {
        const val CHANNEL_ID = "daily_reminder"
        const val RESOLVE_CHANNEL_ID = "ready_to_resolve"
    }
}
