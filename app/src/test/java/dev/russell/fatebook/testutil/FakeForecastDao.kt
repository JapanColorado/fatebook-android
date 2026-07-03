package dev.russell.fatebook.testutil

import dev.russell.fatebook.data.local.ForecastDao
import dev.russell.fatebook.data.local.ForecastEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeForecastDao : ForecastDao {

    private val _forecasts = MutableStateFlow<List<ForecastEntity>>(emptyList())

    val storedForecasts: List<ForecastEntity>
        get() = _forecasts.value

    var deleteAllCallCount = 0
        private set

    override fun observeAll(): Flow<List<ForecastEntity>> = _forecasts

    override suspend fun getByQuestionId(questionId: String): List<ForecastEntity> =
        _forecasts.value
            .filter { it.questionId == questionId }
            .sortedBy { it.createdAtEpochMs }

    override suspend fun upsertAll(forecasts: List<ForecastEntity>) {
        val current = _forecasts.value.toMutableList()
        current.addAll(forecasts)
        _forecasts.value = current
    }

    override suspend fun deleteByQuestionId(questionId: String) {
        _forecasts.value = _forecasts.value.filter { it.questionId != questionId }
    }

    override suspend fun deleteAll() {
        deleteAllCallCount++
        _forecasts.value = emptyList()
    }
}
