package dev.russell.fatebook.data.repository

import dev.russell.fatebook.data.local.CommentDao
import dev.russell.fatebook.data.local.CommentEntity
import dev.russell.fatebook.data.local.ForecastDao
import dev.russell.fatebook.data.local.ForecastEntity
import dev.russell.fatebook.data.local.QuestionDao
import dev.russell.fatebook.data.local.QuestionEntity
import dev.russell.fatebook.data.local.Transactor
import dev.russell.fatebook.data.preferences.UserPreferences
import dev.russell.fatebook.data.remote.FatebookApi
import dev.russell.fatebook.data.remote.dto.CommentDto
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
    private val commentDao: CommentDao,
    private val prefs: UserPreferences,
    private val transactor: Transactor,
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

    suspend fun countReadyToResolve(): Int {
        val todayUtcMs = LocalDate.now()
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        return dao.countReadyToResolve(todayUtcMs)
    }

    fun observeResolved(): Flow<List<Question>> =
        dao.observeResolved().map { entities -> entities.map { it.toDomain() } }

    fun observeAll(): Flow<List<Question>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    fun observeAllForecasts(): Flow<List<ForecastEntity>> =
        forecastDao.observeAll()

    /**
     * Fetch first page from API and merge into local cache as a single transaction.
     * Questions not present in the response are pruned (set-diff). Forecasts and
     * comments cascade-delete via foreign key.
     */
    suspend fun refresh(): List<Question> {
        val response = api.getQuestions()
        nextCursor = response.nextCursor
        val binaryOnly = response.items.filter { it.isBinary }
        commitDtos(binaryOnly, prune = true)
        captureDisplayName(binaryOnly)
        return binaryOnly.map { it.toDomain() }
    }

    /** Load the next page and append to cache. Returns true if more pages exist. */
    suspend fun loadMore(): Boolean {
        val cursor = nextCursor ?: return false
        val response = api.getQuestions(cursor = cursor)
        nextCursor = response.nextCursor
        val binaryOnly = response.items.filter { it.isBinary }
        commitDtos(binaryOnly, prune = false)
        return response.nextCursor != null
    }

    fun hasMore(): Boolean = nextCursor != null

    /**
     * Fetch all pages from API, then merge into the cache as a single transaction.
     * Collecting first means subscribers only see one update at the end, not one per page.
     */
    suspend fun loadAllQuestions() {
        val collected = mutableListOf<QuestionDto>()
        var cursor: Int? = null
        do {
            val response = api.getQuestions(cursor = cursor)
            collected += response.items.filter { it.isBinary }
            cursor = response.nextCursor
            nextCursor = cursor
        } while (cursor != null)
        commitDtos(collected, prune = true)
        captureDisplayName(collected)
    }

    /**
     * Atomic set-diff merge: upsert all [dtos] and (optionally) delete questions
     * not in [dtos]. Forecasts and comments for each refreshed question are
     * replaced wholesale, but only within the affected questions — others are
     * left untouched.
     */
    private suspend fun commitDtos(dtos: List<QuestionDto>, prune: Boolean) {
        val questionEntities = dtos.map { it.toEntity() }
        val keepIds = dtos.map { it.id }
        transactor.transact {
            dao.upsertAll(questionEntities)
            if (prune) {
                // CASCADE on FK removes orphaned forecasts/comments for pruned questions.
                dao.deleteByIdsNotIn(keepIds)
            }
            for (dto in dtos) {
                forecastDao.deleteByQuestionId(dto.id)
                commentDao.deleteByQuestionId(dto.id)
            }
            forecastDao.upsertAll(dtos.flatMap { it.toForecastEntities() })
            commentDao.upsertAll(dtos.flatMap { it.toCommentEntities() })
        }
    }

    private fun captureDisplayName(dtos: List<QuestionDto>) {
        if (prefs.displayName != null) return
        val name = dtos.firstNotNullOfOrNull { dto ->
            dto.forecasts?.firstNotNullOfOrNull { it.user?.name }
        }
        if (name != null) prefs.displayName = name
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
        // Refresh cache to include the new question (server-assigned ID isn't returned).
        // The new refresh is non-destructive, so subscribers see one stable update.
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
        prefs.setLastPredictionDate(System.currentTimeMillis())
        val nowMs = System.currentTimeMillis()
        transactor.transact {
            dao.updateLatestForecast(questionId, forecast, nowMs)
            forecastDao.upsertAll(
                listOf(
                    ForecastEntity(
                        questionId = questionId,
                        forecast = forecast,
                        createdAtEpochMs = nowMs,
                    ),
                ),
            )
        }
    }

    suspend fun resolveQuestion(questionId: String, resolution: Resolution) {
        val apiKey = prefs.apiKey ?: error("No API key configured")
        api.resolveQuestion(
            questionId = questionId,
            resolution = resolution.apiValue,
            apiKey = apiKey,
        )
        dao.updateResolution(questionId, resolution.apiValue)
    }

    suspend fun validateApiKey(): Boolean {
        return try {
            api.validateApiKey()
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Edit question fields. Only non-null params are sent to the API and applied locally. */
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
        dao.updateFields(
            questionId = questionId,
            title = title,
            resolveByEpochMs = resolveBy?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            notes = notes,
            hasNotes = if (notes != null) 1 else 0,
        )
    }

    /** Delete a question. */
    suspend fun deleteQuestion(questionId: String) {
        api.deleteQuestion(questionId = questionId)
        dao.deleteById(questionId)
    }

    /** Load comments for a question from local cache. */
    suspend fun getCommentsForQuestion(questionId: String): List<Comment> {
        return commentDao.getByQuestionId(questionId).map { it.toDomain() }
    }

    /** Add a comment to a question. Returns the newly created Comment. */
    suspend fun addComment(questionId: String, comment: String): Comment {
        val apiKey = prefs.apiKey ?: error("No API key configured")
        api.addComment(questionId = questionId, comment = comment, apiKey = apiKey)
        val now = Instant.now()
        val userName = prefs.displayName
        val newComment = Comment(
            id = "local_${now.toEpochMilli()}",
            userId = "",
            userName = userName,
            comment = comment,
            createdAt = now,
        )
        commentDao.insert(
            CommentEntity(
                id = newComment.id,
                questionId = questionId,
                userId = "",
                userName = userName,
                comment = comment,
                createdAtEpochMs = now.toEpochMilli(),
            )
        )
        return newComment
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
        dao.updateSharing(questionId, sharedPublicly, unlisted)
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

    private fun CommentDto.toDomain(): Comment? {
        val body = comment ?: return null
        return Comment(
            id = id ?: "",
            userId = userId ?: "",
            userName = user?.name,
            comment = body,
            createdAt = createdAt?.let { parseInstant(it) } ?: Instant.EPOCH,
        )
    }

    private fun QuestionDto.toCommentEntities(): List<CommentEntity> =
        comments?.mapNotNull { dto ->
            val comment = dto.toDomain() ?: return@mapNotNull null
            val entityId = dto.id ?: return@mapNotNull null
            CommentEntity(
                id = entityId,
                questionId = id,
                userId = comment.userId,
                userName = comment.userName,
                comment = comment.comment,
                createdAtEpochMs = comment.createdAt.toEpochMilli(),
            )
        } ?: emptyList()

    private fun CommentEntity.toDomain(): Comment = Comment(
        id = id,
        userId = userId,
        userName = userName,
        comment = comment,
        createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    )

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
            comments = comments?.mapNotNull { it.toDomain() } ?: emptyList(),
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
            // Comments loaded separately via getCommentsForQuestion()
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
