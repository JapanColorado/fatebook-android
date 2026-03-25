package dev.russell.fatebook.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.russell.fatebook.data.local.FatebookDatabase
import dev.russell.fatebook.data.local.QuestionDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FatebookDatabase =
        Room.databaseBuilder(
            context,
            FatebookDatabase::class.java,
            "fatebook.db",
        ).build()

    @Provides
    fun provideQuestionDao(database: FatebookDatabase): QuestionDao =
        database.questionDao()
}
