package dev.russell.fatebook.testutil

import dev.russell.fatebook.data.local.OptionDao
import dev.russell.fatebook.data.local.OptionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeOptionDao : OptionDao {

    private val _options = MutableStateFlow<List<OptionEntity>>(emptyList())

    val storedOptions: List<OptionEntity>
        get() = _options.value

    var deleteAllCallCount = 0
        private set

    override fun observeAll(): Flow<List<OptionEntity>> =
        _options.map { options -> options.sortedBy { it.createdAtEpochMs } }

    override suspend fun getByQuestionId(questionId: String): List<OptionEntity> =
        _options.value
            .filter { it.questionId == questionId }
            .sortedBy { it.createdAtEpochMs }

    override suspend fun upsertAll(options: List<OptionEntity>) {
        val byId = _options.value.associateBy { it.id }.toMutableMap()
        for (option in options) {
            byId[option.id] = option
        }
        _options.value = byId.values.toList()
    }

    override suspend fun updateLatestForecast(
        optionId: String,
        forecast: Double,
        forecastAtEpochMs: Long,
    ) {
        _options.value = _options.value.map {
            if (it.id == optionId) {
                it.copy(latestForecast = forecast, latestForecastAtEpochMs = forecastAtEpochMs)
            } else {
                it
            }
        }
    }

    override suspend fun updateResolution(
        optionId: String,
        resolution: String?,
        resolvedAtEpochMs: Long?,
    ) {
        _options.value = _options.value.map {
            if (it.id == optionId) {
                it.copy(resolution = resolution, resolvedAtEpochMs = resolvedAtEpochMs)
            } else {
                it
            }
        }
    }

    override suspend fun deleteByQuestionId(questionId: String) {
        _options.value = _options.value.filter { it.questionId != questionId }
    }

    override suspend fun deleteAll() {
        deleteAllCallCount++
        _options.value = emptyList()
    }
}
