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

    /** Delete every question whose id is NOT in [keepIds]. Used for set-diff refresh. */
    @Query("DELETE FROM questions WHERE id NOT IN (:keepIds)")
    suspend fun deleteByIdsNotIn(keepIds: List<String>)

    @Query("SELECT * FROM questions WHERE id = :questionId")
    suspend fun getById(questionId: String): QuestionEntity?

    @Query("SELECT id FROM questions")
    suspend fun getAllIds(): List<String>

    @Query(
        """
        UPDATE questions
        SET latestForecast = :forecast,
            latestForecastAtEpochMs = :forecastAtEpochMs
        WHERE id = :questionId
        """
    )
    suspend fun updateLatestForecast(
        questionId: String,
        forecast: Double,
        forecastAtEpochMs: Long,
    )

    @Query(
        """
        UPDATE questions
        SET resolved = 1, resolution = :resolution
        WHERE id = :questionId
        """
    )
    suspend fun updateResolution(questionId: String, resolution: String)

    @Query(
        """
        UPDATE questions
        SET title = COALESCE(:title, title),
            resolveByEpochMs = COALESCE(:resolveByEpochMs, resolveByEpochMs),
            notes = CASE WHEN :hasNotes = 1 THEN :notes ELSE notes END
        WHERE id = :questionId
        """
    )
    suspend fun updateFields(
        questionId: String,
        title: String?,
        resolveByEpochMs: Long?,
        notes: String?,
        hasNotes: Int,
    )

    @Query("UPDATE questions SET sharedPublicly = :sharedPublicly, unlisted = :unlisted WHERE id = :questionId")
    suspend fun updateSharing(questionId: String, sharedPublicly: Boolean, unlisted: Boolean)
}
