package dev.russell.fatebook.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * User-authored data, kept apart from the disposable question cache in
 * [FatebookDatabase] (which uses fallbackToDestructiveMigration). Any schema
 * change here needs a real Migration.
 */
@Database(
    entities = [TemplateEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class TemplateDatabase : RoomDatabase() {
    abstract fun templateDao(): TemplateDao
}
