package dev.russell.fatebook.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One queued mutation that needs to be replayed against the Fatebook API.
 * Inserted inside the same Room transaction as the optimistic local update,
 * so the queue and the visible state never disagree.
 */
@Entity(
    tableName = "pending_mutations",
    indices = [
        Index("status"),
        Index("questionLocalId"),
    ],
)
data class PendingMutationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val questionLocalId: String,
    val payloadJson: String,
    val createdAtEpochMs: Long,
    val attemptCount: Int = 0,
    val lastError: String? = null,
    val status: String = STATUS_PENDING,
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_IN_FLIGHT = "IN_FLIGHT"
        const val STATUS_ERRORED = "ERRORED"

        const val TYPE_CREATE_QUESTION = "CREATE_QUESTION"
        const val TYPE_ADD_FORECAST = "ADD_FORECAST"
        const val TYPE_RESOLVE = "RESOLVE"
        const val TYPE_EDIT = "EDIT"
        const val TYPE_DELETE = "DELETE"
        const val TYPE_SET_SHARED = "SET_SHARED"
        const val TYPE_ADD_COMMENT = "ADD_COMMENT"

        const val LOCAL_ID_PREFIX = "local-"
    }
}
