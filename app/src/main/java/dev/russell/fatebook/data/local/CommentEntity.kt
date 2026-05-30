package dev.russell.fatebook.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "comments",
    foreignKeys = [ForeignKey(
        entity = QuestionEntity::class,
        parentColumns = ["id"],
        childColumns = ["questionId"],
        onDelete = ForeignKey.CASCADE,
        onUpdate = ForeignKey.CASCADE,
    )],
    indices = [Index("questionId")],
)
data class CommentEntity(
    @PrimaryKey val id: String,
    val questionId: String,
    val userId: String,
    val userName: String? = null,
    val comment: String,
    val createdAtEpochMs: Long,
)
