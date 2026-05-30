package dev.russell.fatebook.testutil

import dev.russell.fatebook.data.local.PendingMutationDao
import dev.russell.fatebook.data.local.PendingMutationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakePendingMutationDao : PendingMutationDao {

    private val _rows = MutableStateFlow<List<PendingMutationEntity>>(emptyList())
    private var nextId = 1L

    val stored: List<PendingMutationEntity>
        get() = _rows.value

    override suspend fun insert(mutation: PendingMutationEntity): Long {
        val id = nextId++
        val withId = mutation.copy(id = id)
        _rows.value = _rows.value + withId
        return id
    }

    override suspend fun nextPending(): PendingMutationEntity? =
        _rows.value.firstOrNull { it.status != PendingMutationEntity.STATUS_ERRORED }

    override fun observeAll(): Flow<List<PendingMutationEntity>> = _rows

    override fun observeErrored(): Flow<List<PendingMutationEntity>> =
        _rows.map { list -> list.filter { it.status == PendingMutationEntity.STATUS_ERRORED } }

    override fun observeErroredCount(): Flow<Int> =
        _rows.map { list -> list.count { it.status == PendingMutationEntity.STATUS_ERRORED } }

    override suspend fun markInFlight(id: Long) {
        _rows.value = _rows.value.map {
            if (it.id == id) it.copy(status = PendingMutationEntity.STATUS_IN_FLIGHT) else it
        }
    }

    override suspend fun markPending(id: Long) {
        _rows.value = _rows.value.map {
            if (it.id == id) it.copy(status = PendingMutationEntity.STATUS_PENDING) else it
        }
    }

    override suspend fun markErrored(id: Long, error: String?) {
        _rows.value = _rows.value.map {
            if (it.id == id) it.copy(
                status = PendingMutationEntity.STATUS_ERRORED,
                attemptCount = it.attemptCount + 1,
                lastError = error,
            ) else it
        }
    }

    override suspend fun markPendingAfterAttempt(id: Long, error: String?) {
        _rows.value = _rows.value.map {
            if (it.id == id) it.copy(
                status = PendingMutationEntity.STATUS_PENDING,
                attemptCount = it.attemptCount + 1,
                lastError = error,
            ) else it
        }
    }

    override suspend fun retryAllErrored() {
        _rows.value = _rows.value.map {
            if (it.status == PendingMutationEntity.STATUS_ERRORED) {
                it.copy(status = PendingMutationEntity.STATUS_PENDING, lastError = null)
            } else it
        }
    }

    override suspend fun resetInFlight() {
        _rows.value = _rows.value.map {
            if (it.status == PendingMutationEntity.STATUS_IN_FLIGHT) {
                it.copy(status = PendingMutationEntity.STATUS_PENDING)
            } else it
        }
    }

    override suspend fun delete(id: Long) {
        _rows.value = _rows.value.filter { it.id != id }
    }

    override suspend fun deleteByQuestion(questionLocalId: String) {
        _rows.value = _rows.value.filter { it.questionLocalId != questionLocalId }
    }

    override suspend fun rewriteQuestionId(oldId: String, newId: String) {
        _rows.value = _rows.value.map {
            if (it.questionLocalId == oldId) it.copy(questionLocalId = newId) else it
        }
    }

    override suspend fun getByQuestion(questionLocalId: String): List<PendingMutationEntity> =
        _rows.value.filter { it.questionLocalId == questionLocalId }

    override suspend fun getById(id: Long): PendingMutationEntity? =
        _rows.value.firstOrNull { it.id == id }
}
