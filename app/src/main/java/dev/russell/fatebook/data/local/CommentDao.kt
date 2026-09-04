package dev.russell.fatebook.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Projection for feed search: just enough to match a query against comment text. */
data class CommentSearchRow(
    val questionId: String,
    val comment: String,
)

@Dao
interface CommentDao {

    @Query("SELECT * FROM comments WHERE questionId = :questionId ORDER BY createdAtEpochMs ASC")
    suspend fun getByQuestionId(questionId: String): List<CommentEntity>

    @Query("SELECT questionId, comment FROM comments")
    fun observeSearchIndex(): Flow<List<CommentSearchRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(comments: List<CommentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(comment: CommentEntity)

    @Query("DELETE FROM comments WHERE questionId = :questionId")
    suspend fun deleteByQuestionId(questionId: String)

    @Query("DELETE FROM comments")
    suspend fun deleteAll()
}
