package dev.russell.fatebook.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [QuestionEntity::class, ForecastEntity::class, CommentEntity::class],
    version = 8,
    exportSchema = false,
)
abstract class FatebookDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
    abstract fun forecastDao(): ForecastDao
    abstract fun commentDao(): CommentDao
}
