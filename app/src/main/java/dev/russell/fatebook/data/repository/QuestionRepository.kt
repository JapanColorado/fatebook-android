package dev.russell.fatebook.data.repository

import dev.russell.fatebook.data.local.QuestionDao
import dev.russell.fatebook.data.local.QuestionEntity
import dev.russell.fatebook.data.preferences.UserPreferences
import dev.russell.fatebook.data.remote.FatebookApi
import dev.russell.fatebook.data.remote.dto.QuestionDto
import dev.russell.fatebook.domain.model.Forecast
import dev.russell.fatebook.domain.model.Question
import dev.russell.fatebook.domain.model.Resolution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuestionRepository @Inject constructor(
    private val api: FatebookApi,
    private val dao: QuestionDao,
    private val prefs: UserPreferences,
) {
    /** Observe cached questions, mapped to domain models. */
    fun observeActive(): Flow<List<Question>> =
        dao.observeActive().map { entities -> entities.map { it.toDomain() } }

    fun observeReadyToResolve(): Flow<List<Question>> =
        dao.observeReadyToResolve(System.currentTimeMillis())
            .map { entities -> entities.map { it.toDomain() } }

    fun observeResolved(): Flow<List<Question>> =
        dao.observeResolved().map { entities -> entities.map { it.toDomain() } }

    fun observeAll(): Flow<List<Question>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    /** Fetch from API and update local cache. Returns the fetched questions. */
    suspend fun refresh(): List<Question> {
        val response = api.getQuestions()
        val entities = response.items.map { it.toEntity() }
        dao.deleteAll()
        dao.upsertAll(entities)
        return response.items.map { it.toDomain() }
    }

    suspend fun createQuestion(
        title: String,
        resolveBy: LocalDate,
        forecast: Double,
    ): String {
        val resolveByStr = resolveBy.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val url = api.createQuestion(title, resolveByStr, forecast)
        // Record that user made a prediction today
        prefs.setLastPredictionDate(System.currentTimeMillis())
        // Refresh cache to include the new question
        refresh()
        return url
    }

    suspend fun resolveQuestion(questionId: String, resolution: Resolution) {
        api.resolveQuestion(
            questionId = questionId,
            resolution = resolution.apiValue,
        )
        refresh()
    }

    suspend fun validateApiKey(): Boolean {
        return try {
            api.validateApiKey()
            true
        } catch (_: Exception) {
            false
        }
    }

    // --- Mappers ---

    private fun QuestionDto.toEntity(): QuestionEntity {
        val latestForecast = forecasts
            ?.filter { it.forecast != null }
            ?.maxByOrNull { it.createdAt ?: "" }
            ?.forecast

        return QuestionEntity(
            id = id,
            title = title,
            resolveByEpochMs = parseInstant(resolveBy).toEpochMilli(),
            createdAtEpochMs = parseInstant(createdAt).toEpochMilli(),
            resolution = resolution,
            resolved = resolved,
            latestForecast = latestForecast,
            url = url ?: "https://fatebook.io/q/$id",
        )
    }

    private fun QuestionDto.toDomain(): Question {
        return Question(
            id = id,
            title = title,
            resolveBy = parseInstant(resolveBy),
            createdAt = parseInstant(createdAt),
            resolution = resolution?.let { Resolution.fromApi(it) },
            resolved = resolved,
            yourLatestForecast = forecasts
                ?.filter { it.forecast != null }
                ?.maxByOrNull { it.createdAt ?: "" }
                ?.forecast,
            forecasts = forecasts?.map { dto ->
                Forecast(
                    userId = dto.userId ?: "",
                    forecast = dto.forecast,
                    createdAt = dto.createdAt?.let { parseInstant(it) } ?: Instant.EPOCH,
                )
            } ?: emptyList(),
            url = url ?: "https://fatebook.io/q/$id",
        )
    }

    private fun QuestionEntity.toDomain(): Question {
        return Question(
            id = id,
            title = title,
            resolveBy = Instant.ofEpochMilli(resolveByEpochMs),
            createdAt = Instant.ofEpochMilli(createdAtEpochMs),
            resolution = resolution?.let { Resolution.fromApi(it) },
            resolved = resolved,
            yourLatestForecast = latestForecast,
            forecasts = emptyList(), // Not stored locally
            url = url,
        )
    }

    private fun parseInstant(dateStr: String): Instant {
        return try {
            Instant.parse(dateStr)
        } catch (_: Exception) {
            try {
                // Handle YYYY-MM-DD format (defaults to midnight UTC)
                LocalDate.parse(dateStr)
                    .atStartOfDay(ZoneId.of("UTC"))
                    .toInstant()
            } catch (_: Exception) {
                Instant.EPOCH
            }
        }
    }
}
