package dev.russell.fatebook.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "options",
    foreignKeys = [ForeignKey(
        entity = QuestionEntity::class,
        parentColumns = ["id"],
        childColumns = ["questionId"],
        onDelete = ForeignKey.CASCADE,
        onUpdate = ForeignKey.CASCADE,
    )],
    indices = [Index("questionId")],
)
data class OptionEntity(
    @PrimaryKey val id: String, // server option id
    val questionId: String,
    val text: String,
    val createdAtEpochMs: Long,
    val resolution: String?, // YES / NO / AMBIGUOUS / null, per option
    val resolvedAtEpochMs: Long? = null,
    val latestForecast: Double? = null,
    val latestForecastAtEpochMs: Long? = null,
)
