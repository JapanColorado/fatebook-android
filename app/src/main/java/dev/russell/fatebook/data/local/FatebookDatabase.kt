package dev.russell.fatebook.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [QuestionEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class FatebookDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
}
