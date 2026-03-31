package dev.russell.fatebook.data.repository

import dev.russell.fatebook.data.local.ForecastDao
import dev.russell.fatebook.data.local.ForecastEntity
import dev.russell.fatebook.data.local.QuestionDao
import dev.russell.fatebook.data.local.QuestionEntity
import dev.russell.fatebook.data.preferences.UserPreferences
import dev.russell.fatebook.data.remote.FatebookApi
import dev.russell.fatebook.data.remote.dto.QuestionDto
import dev.russell.fatebook.domain.model.Comment
import dev.russell.fatebook.domain.model.Forecast
import dev.russell.fatebook.domain.model.Question
import dev.russell.fatebook.domain.model.Resolution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuestionRepository @Inject constructor(
    private val api: FatebookApi,
    private val dao: QuestionDao,
    private val forecastDao: ForecastDao,
    private val prefs: UserPreferences,
) {
    private var nextCursor: Int? = null

    private val QuestionDto.isBinary: Boolean
        get() = questionType == null || questionType == "BINARY"

    /** Observe cached questions, mapped to domain models. */
    fun observeActive(): Flow<List<Question>> =
        dao.observeActive().map { entities -> entities.map { it.toDomain() } }

    fun observeReadyToResolve(): Flow<List<Question>> {
        // Convert today's local date to UTC midnight epoch ms for comparison
        // against the stored UTC-midnight resolveByEpochMs values
        val todayUtcMs = LocalDate.now()
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        return dao.observeReadyToResolve(todayUtcMs)
            .map { entities -> entities.map { it.toDomain() } }
    }

    fun observeResolved(): Flow<List<Question>> =
        dao.observeResolved().map { entities -> entities.map { it.toDomain() } }

    fun observeAll(): Flow<List<Question>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    fun observeAllForecasts(): Flow<List<ForecastEntity>> =
        forecastDao.observeAll()

    /** Fetch first page from API and replace local cache. */
    suspend fun refresh(): List<Question> {
        val response = api.getQuestions()
        nextCursor = response.nextCursor
        val binaryOnly = response.items.filter { it.isBinary }
        val entities = binaryOnly.map { it.toEntity() }
        dao.deleteAll()
        forecastDao.deleteAll()
        dao.upsertAll(entities)
        forecastDao.upsertAll(binaryOnly.flatMap { it.toForecastEntities() })
        return binaryOnly.map { it.toDomain() }
    }

    /** Load the next page and append to cache. Returns true if more pages exist. */
    suspend fun loadMore(): Boolean {
        val cursor = nextCursor ?: return false
        val response = api.getQuestions(cursor = cursor)
        nextCursor = response.nextCursor
        val binaryOnly = response.items.filter { it.isBinary }
        val entities = binaryOnly.map { it.toEntity() }
        dao.upsertAll(entities)
        forecastDao.upsertAll(binaryOnly.flatMap { it.toForecastEntities() })
        return response.nextCursor != null
    }

    fun hasMore(): Boolean = nextCursor != null

    /** Fetch all pages from API, populating the local cache. */
    suspend fun loadAllQuestions() {
        refresh()
        while (hasMore()) {
            loadMore()
        }
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

    suspend fun addForecast(questionId: String, forecast: Double) {
        val apiKey = prefs.apiKey ?: error("No API key configured")
        api.addForecast(
            questionId = questionId,
            forecast = forecast,
            apiKey = apiKey,
        )
        // Record that user made a prediction today
        prefs.setLastPredictionDate(System.currentTimeMillis())
        refresh()
    }

    suspend fun resolveQuestion(questionId: String, resolution: Resolution) {
        val apiKey = prefs.apiKey ?: error("No API key configured")
        api.resolveQuestion(
            questionId = questionId,
            resolution = resolution.apiValue,
            apiKey = apiKey,
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

    /** Fetch a single question by ID (for deep links and enriching detail view). */
    suspend fun getQuestion(questionId: String): Question {
        val dto = api.getQuestion(questionId)
        return dto.toDomain()
    }

    /** Edit question fields. Only non-null params are sent to the API. */
    suspend fun editQuestion(
        questionId: String,
        title: String? = null,
        resolveBy: LocalDate? = null,
        notes: String? = null,
    ) {
        val apiKey = prefs.apiKey ?: error("No API key configured")
        api.editQuestion(
            questionId = questionId,
            title = title,
            resolveBy = resolveBy?.format(DateTimeFormatter.ISO_LOCAL_DATE),
            notes = notes,
            apiKey = apiKey,
        )
        refresh()
    }

    /** Delete a question. */
    suspend fun deleteQuestion(questionId: String) {
        val apiKey = prefs.apiKey ?: error("No API key configured")
        api.deleteQuestion(questionId = questionId, apiKey = apiKey)
        dao.deleteById(questionId)
    }

    /** Add a comment to a question. Returns the updated question with comments. */
    suspend fun addComment(questionId: String, comment: String): Question {
        val apiKey = prefs.apiKey ?: error("No API key configured")
        api.addComment(questionId = questionId, comment = comment, apiKey = apiKey)
        // Re-fetch the question to get updated comments list
        return getQuestion(questionId)
    }

    /** Toggle public visibility of a question. */
    suspend fun setSharedPublicly(
        questionId: String,
        sharedPublicly: Boolean,
        unlisted: Boolean,
    ) {
        val apiKey = prefs.apiKey ?: error("No API key configured")
        api.setSharedPublicly(
            questionId = questionId,
            sharedPublicly = sharedPublicly,
            unlisted = unlisted,
            apiKey = apiKey,
        )
        refresh()
    }

    /** Look up a question in the local cache by ID. */
    suspend fun getCachedQuestion(questionId: String): Question? {
        return dao.getById(questionId)?.toDomain()
    }

    // --- Mappers ---

    private fun QuestionDto.toEntity(): QuestionEntity {
        val latest = forecasts
            ?.filter { it.forecast != null }
            ?.maxByOrNull { it.createdAt ?: "" }

        return QuestionEntity(
            id = id,
            title = title,
            resolveByEpochMs = parseInstant(resolveBy).toEpochMilli(),
            createdAtEpochMs = parseInstant(createdAt).toEpochMilli(),
            resolution = resolution,
            resolved = resolved,
            latestForecast = latest?.forecast,
            latestForecastAtEpochMs = latest?.createdAt?.let { parseInstant(it).toEpochMilli() },
            url = url ?: "https://fatebook.io/q/$id",
            forecastHiddenUntilEpochMs = latest?.hideForecastsUntil?.let {
                try { parseInstant(it).toEpochMilli() } catch (_: Exception) { null }
            },
            notes = notes,
            sharedPublicly = sharedPublicly ?: false,
            unlisted = unlisted ?: false,
        )
    }

    private fun QuestionDto.toForecastEntities(): List<ForecastEntity> =
        forecasts
            ?.filter { it.forecast != null && it.createdAt != null }
            ?.map { dto ->
                ForecastEntity(
                    questionId = id,
                    forecast = dto.forecast!!,
                    createdAtEpochMs = parseInstant(dto.createdAt!!).toEpochMilli(),
                )
            } ?: emptyList()

    private fun QuestionDto.toDomain(): Question {
        val latest = forecasts
            ?.filter { it.forecast != null }
            ?.maxByOrNull { it.createdAt ?: "" }

        return Question(
            id = id,
            title = title,
            resolveBy = parseInstant(resolveBy),
            createdAt = parseInstant(createdAt),
            resolution = resolution?.let { Resolution.fromApi(it) },
            resolved = resolved,
            yourLatestForecast = latest?.forecast,
            latestForecastAt = latest?.createdAt?.let { parseInstant(it) },
            forecasts = forecasts?.map { dto ->
                Forecast(
                    userId = dto.userId ?: "",
                    forecast = dto.forecast,
                    createdAt = dto.createdAt?.let { parseInstant(it) } ?: Instant.EPOCH,
                )
            } ?: emptyList(),
            url = url ?: "https://fatebook.io/q/$id",
            forecastHiddenUntil = latest?.hideForecastsUntil?.let {
                try { parseInstant(it) } catch (_: Exception) { null }
            },
            notes = notes,
            sharedPublicly = sharedPublicly ?: false,
            unlisted = unlisted ?: false,
            comments = comments?.mapNotNull { dto ->
                if (dto.comment == null) null
                else Comment(
                    id = dto.id ?: "",
                    userId = dto.userId ?: "",
                    comment = dto.comment,
                    createdAt = dto.createdAt?.let { parseInstant(it) } ?: Instant.EPOCH,
                )
            } ?: emptyList(),
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
            latestForecastAt = latestForecastAtEpochMs?.let { Instant.ofEpochMilli(it) },
            forecasts = emptyList(), // Not stored locally
            url = url,
            forecastHiddenUntil = forecastHiddenUntilEpochMs?.let { Instant.ofEpochMilli(it) },
            notes = notes,
            sharedPublicly = sharedPublicly,
            unlisted = unlisted,
            // Comments not cached locally — fetched on-demand via getQuestion()
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
