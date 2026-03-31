package dev.russell.fatebook.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {

    @Query("SELECT * FROM questions ORDER BY resolveByEpochMs ASC")
    fun observeAll(): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE resolved = 0 ORDER BY resolveByEpochMs ASC")
    fun observeActive(): Flow<List<QuestionEntity>>

    @Query(
        """
        SELECT * FROM questions
        WHERE resolved = 0 AND resolveByEpochMs <= :nowEpochMs
        ORDER BY resolveByEpochMs ASC
        """
    )
    fun observeReadyToResolve(nowEpochMs: Long): Flow<List<QuestionEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM questions
        WHERE resolved = 0 AND resolveByEpochMs <= :nowEpochMs
        """
    )
    suspend fun countReadyToResolve(nowEpochMs: Long): Int

    @Query("SELECT * FROM questions WHERE resolved = 1 ORDER BY resolveByEpochMs DESC")
    fun observeResolved(): Flow<List<QuestionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(questions: List<QuestionEntity>)

    @Query("DELETE FROM questions")
    suspend fun deleteAll()

    @Query("DELETE FROM questions WHERE id = :questionId")
    suspend fun deleteById(questionId: String)

    @Query("SELECT * FROM questions WHERE id = :questionId")
    suspend fun getById(questionId: String): QuestionEntity?
}
