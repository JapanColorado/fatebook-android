package dev.russell.fatebook.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A saved question pattern for recurring predictions. Lives in its own
 * [TemplateDatabase] because the cache DB is destructively migrated.
 */
@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val defaultProbability: Double,
    val tagsJson: String,
    /** Days from "today" to the default resolve-by date when applied. */
    val resolveOffsetDays: Int,
    val createdAtEpochMs: Long,
)
