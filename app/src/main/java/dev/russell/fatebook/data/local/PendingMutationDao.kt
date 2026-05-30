package dev.russell.fatebook.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingMutationDao {

    @Insert
    suspend fun insert(mutation: PendingMutationEntity): Long

    @Query(
        """
        SELECT * FROM pending_mutations
        WHERE status != 'ERRORED'
        ORDER BY id ASC
        LIMIT 1
        """
    )
    suspend fun nextPending(): PendingMutationEntity?

    @Query("SELECT * FROM pending_mutations ORDER BY id ASC")
    fun observeAll(): Flow<List<PendingMutationEntity>>

    @Query("SELECT * FROM pending_mutations WHERE status = 'ERRORED' ORDER BY id ASC")
    fun observeErrored(): Flow<List<PendingMutationEntity>>

    @Query("SELECT COUNT(*) FROM pending_mutations WHERE status = 'ERRORED'")
    fun observeErroredCount(): Flow<Int>

    @Query("UPDATE pending_mutations SET status = 'IN_FLIGHT' WHERE id = :id")
    suspend fun markInFlight(id: Long)

    @Query("UPDATE pending_mutations SET status = 'PENDING' WHERE id = :id")
    suspend fun markPending(id: Long)

    @Query(
        """
        UPDATE pending_mutations
        SET status = 'ERRORED',
            attemptCount = attemptCount + 1,
            lastError = :error
        WHERE id = :id
        """
    )
    suspend fun markErrored(id: Long, error: String?)

    @Query(
        """
        UPDATE pending_mutations
        SET status = 'PENDING',
            attemptCount = attemptCount + 1,
            lastError = :error
        WHERE id = :id
        """
    )
    suspend fun markPendingAfterAttempt(id: Long, error: String?)

    /**
     * Reset all ERRORED rows back to PENDING. Triggered by the "Retry" button on
     * the sync-issues banner.
     */
    @Query("UPDATE pending_mutations SET status = 'PENDING', lastError = NULL WHERE status = 'ERRORED'")
    suspend fun retryAllErrored()

    /**
     * Reset rows that were left IN_FLIGHT by a crashed worker run so the next
     * worker picks them up again.
     */
    @Query("UPDATE pending_mutations SET status = 'PENDING' WHERE status = 'IN_FLIGHT'")
    suspend fun resetInFlight()

    @Query("DELETE FROM pending_mutations WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM pending_mutations WHERE questionLocalId = :questionLocalId")
    suspend fun deleteByQuestion(questionLocalId: String)

    /**
     * Rewrite the questionLocalId for all rows that target an old (temp) id.
     * Called once a CREATE_QUESTION syncs and we learn the real server id.
     */
    @Query("UPDATE pending_mutations SET questionLocalId = :newId WHERE questionLocalId = :oldId")
    suspend fun rewriteQuestionId(oldId: String, newId: String)

    @Query("SELECT * FROM pending_mutations WHERE questionLocalId = :questionLocalId ORDER BY id ASC")
    suspend fun getByQuestion(questionLocalId: String): List<PendingMutationEntity>

    @Query("SELECT * FROM pending_mutations WHERE id = :id")
    suspend fun getById(id: Long): PendingMutationEntity?
}
