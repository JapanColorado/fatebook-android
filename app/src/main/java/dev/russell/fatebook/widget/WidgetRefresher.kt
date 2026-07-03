package dev.russell.fatebook.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Indirection over Glance widget updates so pure-Kotlin classes (SyncRunner)
 * can trigger them without an Android dependency — tests pass a no-op.
 */
fun interface WidgetRefresher {
    suspend fun refresh()
}

@Module
@InstallIn(SingletonComponent::class)
object WidgetModule {

    @Provides
    @Singleton
    fun provideWidgetRefresher(@ApplicationContext context: Context): WidgetRefresher =
        WidgetRefresher { FatebookWidget().updateAll(context) }
}
