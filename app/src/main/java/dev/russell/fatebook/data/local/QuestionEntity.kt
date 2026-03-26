package dev.russell.fatebook.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "questions")
data class QuestionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val resolveByEpochMs: Long,
    val createdAtEpochMs: Long,
    val resolution: String?, // YES / NO / AMBIGUOUS / null
    val resolved: Boolean,
    val latestForecast: Double?,
    val latestForecastAtEpochMs: Long?,
    val url: String,
    val lastSyncedEpochMs: Long = System.currentTimeMillis(),
)
