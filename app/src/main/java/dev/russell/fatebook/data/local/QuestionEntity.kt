package dev.russell.fatebook.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "questions",
    indices = [
        Index("resolved"),
        Index("resolveByEpochMs"),
    ],
)
data class QuestionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val resolveByEpochMs: Long,
    val createdAtEpochMs: Long,
    val resolution: String?, // YES / NO / AMBIGUOUS / null
    val resolved: Boolean,
    val resolvedAtEpochMs: Long? = null,
    val latestForecast: Double?,
    val latestForecastAtEpochMs: Long?,
    val url: String,
    val forecastHiddenUntilEpochMs: Long? = null,
    val lastSyncedEpochMs: Long = System.currentTimeMillis(),
    val notes: String? = null,
    val sharedPublicly: Boolean = false,
    val unlisted: Boolean = false,
)
