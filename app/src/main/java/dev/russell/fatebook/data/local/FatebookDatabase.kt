package dev.russell.fatebook.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        QuestionEntity::class,
        ForecastEntity::class,
        CommentEntity::class,
        OptionEntity::class,
        PendingMutationEntity::class,
    ],
    version = 11,
    exportSchema = false,
)
abstract class FatebookDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
    abstract fun forecastDao(): ForecastDao
    abstract fun commentDao(): CommentDao
    abstract fun optionDao(): OptionDao
    abstract fun pendingMutationDao(): PendingMutationDao
}
