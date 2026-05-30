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

    /**
     * Find a non-local question matching [title] whose createdAt is within
     * [windowMs] of [aroundEpochMs]. Used as a fallback to reconcile a
     * locally-created question with the server-assigned row when URL parsing
     * fails. Title equality is the strongest signal we have that's resilient
     * to server-side resolveBy timezone normalisation.
     */
    @Query(
        """
        SELECT * FROM questions
        WHERE id NOT LIKE 'local-%'
          AND title = :title
          AND ABS(createdAtEpochMs - :aroundEpochMs) <= :windowMs
        ORDER BY ABS(createdAtEpochMs - :aroundEpochMs) ASC, createdAtEpochMs DESC
        LIMIT 1
        """
    )
    suspend fun findCreatedNear(
        title: String,
        aroundEpochMs: Long,
        windowMs: Long,
    ): QuestionEntity?

    /** Exact-URL lookup. Used as a fallback when URL-id parsing fails. */
    @Query("SELECT * FROM questions WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): QuestionEntity?

    /**
     * Most-recent non-local question created within [windowMs] of [aroundEpochMs].
     * Last-resort fallback for matching a freshly-created server question when
     * URL parsing AND title matching both fail.
     */
    @Query(
        """
        SELECT * FROM questions
        WHERE id NOT LIKE 'local-%'
          AND ABS(createdAtEpochMs - :aroundEpochMs) <= :windowMs
        ORDER BY createdAtEpochMs DESC
        LIMIT 1
        """
    )
    suspend fun findMostRecentNonLocalNear(aroundEpochMs: Long, windowMs: Long): QuestionEntity?

    /** First N non-local questions, most recent first. Used to enrich sync error messages. */
    @Query("SELECT * FROM questions WHERE id NOT LIKE 'local-%' ORDER BY createdAtEpochMs DESC LIMIT :limit")
    suspend fun recentNonLocal(limit: Int): List<QuestionEntity>

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
        SET resolved = 1, resolution = :resolution, resolvedAtEpochMs = :resolvedAtEpochMs
        WHERE id = :questionId
        """
    )
    suspend fun updateResolution(questionId: String, resolution: String, resolvedAtEpochMs: Long)

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
