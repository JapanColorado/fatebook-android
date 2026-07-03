package dev.russell.fatebook.di

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.russell.fatebook.data.local.FatebookDatabase
import dev.russell.fatebook.data.local.CommentDao
import dev.russell.fatebook.data.local.ForecastDao
import dev.russell.fatebook.data.local.OptionDao
import dev.russell.fatebook.data.local.PendingMutationDao
import dev.russell.fatebook.data.local.QuestionDao
import dev.russell.fatebook.data.local.Transactor
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
        ).fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideQuestionDao(database: FatebookDatabase): QuestionDao =
        database.questionDao()

    @Provides
    fun provideForecastDao(database: FatebookDatabase): ForecastDao =
        database.forecastDao()

    @Provides
    fun provideCommentDao(database: FatebookDatabase): CommentDao =
        database.commentDao()

    @Provides
    fun provideOptionDao(database: FatebookDatabase): OptionDao =
        database.optionDao()

    @Provides
    fun providePendingMutationDao(database: FatebookDatabase): PendingMutationDao =
        database.pendingMutationDao()

    @Provides
    @Singleton
    fun provideTransactor(database: FatebookDatabase): Transactor =
        object : Transactor {
            override suspend fun transact(block: suspend () -> Unit) {
                database.withTransaction { block() }
            }
        }
}
