package dev.russell.fatebook.testutil

import dev.russell.fatebook.data.local.QuestionDao
import dev.russell.fatebook.data.local.QuestionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeQuestionDao : QuestionDao {

    private val _questions = MutableStateFlow<List<QuestionEntity>>(emptyList())

    val storedQuestions: List<QuestionEntity>
        get() = _questions.value

    var deleteAllCallCount = 0
        private set

    var deleteByIdsNotInCallCount = 0
        private set

    override fun observeAll(): Flow<List<QuestionEntity>> =
        _questions.map { it.sortedBy { q -> q.resolveByEpochMs } }

    override fun observeActive(): Flow<List<QuestionEntity>> =
        _questions.map { list ->
            list.filter { !it.resolved }.sortedBy { it.resolveByEpochMs }
        }

    override fun observeReadyToResolve(nowEpochMs: Long): Flow<List<QuestionEntity>> =
        _questions.map { list ->
            list.filter { !it.resolved && it.resolveByEpochMs <= nowEpochMs }
                .sortedBy { it.resolveByEpochMs }
        }

    override suspend fun countReadyToResolve(nowEpochMs: Long): Int =
        _questions.value.count { !it.resolved && it.resolveByEpochMs <= nowEpochMs }

    override fun observeResolved(): Flow<List<QuestionEntity>> =
        _questions.map { list ->
            list.filter { it.resolved }.sortedByDescending { it.resolveByEpochMs }
        }

    override suspend fun upsertAll(questions: List<QuestionEntity>) {
        val current = _questions.value.toMutableList()
        for (q in questions) {
            current.removeAll { it.id == q.id }
            current.add(q)
        }
        _questions.value = current
    }

    override suspend fun deleteAll() {
        deleteAllCallCount++
        _questions.value = emptyList()
    }

    override suspend fun deleteById(questionId: String) {
        _questions.value = _questions.value.filter { it.id != questionId }
    }

    override suspend fun deleteByIdsNotIn(keepIds: List<String>) {
        deleteByIdsNotInCallCount++
        _questions.value = _questions.value.filter { it.id in keepIds }
    }

    override suspend fun getById(questionId: String): QuestionEntity? {
        return _questions.value.find { it.id == questionId }
    }

    override suspend fun getAllIds(): List<String> =
        _questions.value.map { it.id }

    override suspend fun updateLatestForecast(
        questionId: String,
        forecast: Double,
        forecastAtEpochMs: Long,
    ) {
        _questions.value = _questions.value.map {
            if (it.id == questionId) it.copy(
                latestForecast = forecast,
                latestForecastAtEpochMs = forecastAtEpochMs,
            ) else it
        }
    }

    override suspend fun updateResolution(
        questionId: String,
        resolution: String,
        resolvedAtEpochMs: Long,
    ) {
        _questions.value = _questions.value.map {
            if (it.id == questionId) {
                it.copy(
                    resolved = true,
                    resolution = resolution,
                    resolvedAtEpochMs = resolvedAtEpochMs,
                )
            } else {
                it
            }
        }
    }

    override suspend fun updateFields(
        questionId: String,
        title: String?,
        resolveByEpochMs: Long?,
        notes: String?,
        hasNotes: Int,
    ) {
        _questions.value = _questions.value.map {
            if (it.id == questionId) it.copy(
                title = title ?: it.title,
                resolveByEpochMs = resolveByEpochMs ?: it.resolveByEpochMs,
                notes = if (hasNotes == 1) notes else it.notes,
            ) else it
        }
    }

    override suspend fun updateSharing(
        questionId: String,
        sharedPublicly: Boolean,
        unlisted: Boolean,
    ) {
        _questions.value = _questions.value.map {
            if (it.id == questionId) it.copy(
                sharedPublicly = sharedPublicly,
                unlisted = unlisted,
            ) else it
        }
    }

    override suspend fun findCreatedNear(
        title: String,
        aroundEpochMs: Long,
        windowMs: Long,
    ): QuestionEntity? {
        return _questions.value.asSequence()
            .filter { !it.id.startsWith("local-") }
            .filter { it.title == title }
            .filter { kotlin.math.abs(it.createdAtEpochMs - aroundEpochMs) <= windowMs }
            .sortedBy { kotlin.math.abs(it.createdAtEpochMs - aroundEpochMs) }
            .firstOrNull()
    }

    override suspend fun findByUrl(url: String): QuestionEntity? =
        _questions.value.firstOrNull { it.url == url }

    override suspend fun findMostRecentNonLocalNear(
        aroundEpochMs: Long,
        windowMs: Long,
    ): QuestionEntity? = _questions.value
        .filter { !it.id.startsWith("local-") }
        .filter { kotlin.math.abs(it.createdAtEpochMs - aroundEpochMs) <= windowMs }
        .maxByOrNull { it.createdAtEpochMs }

    override suspend fun recentNonLocal(limit: Int): List<QuestionEntity> =
        _questions.value
            .filter { !it.id.startsWith("local-") }
            .sortedByDescending { it.createdAtEpochMs }
            .take(limit)
}
