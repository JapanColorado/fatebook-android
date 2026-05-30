package dev.russell.fatebook.data.repository

import dev.russell.fatebook.data.local.CommentDao
import dev.russell.fatebook.data.local.CommentEntity
import dev.russell.fatebook.data.local.ForecastDao
import dev.russell.fatebook.data.local.ForecastEntity
import dev.russell.fatebook.data.local.PendingMutationDao
import dev.russell.fatebook.data.local.PendingMutationEntity
import dev.russell.fatebook.data.local.QuestionDao
import dev.russell.fatebook.data.local.QuestionEntity
import dev.russell.fatebook.data.local.Transactor
import dev.russell.fatebook.data.preferences.UserPreferences
import dev.russell.fatebook.data.remote.FatebookApi
import dev.russell.fatebook.data.remote.dto.CommentDto
import dev.russell.fatebook.data.remote.dto.QuestionDto
import dev.russell.fatebook.data.sync.AddCommentPayload
import dev.russell.fatebook.data.sync.AddForecastPayload
import dev.russell.fatebook.data.sync.CreateQuestionPayload
import dev.russell.fatebook.data.sync.EditPayload
import dev.russell.fatebook.data.sync.MutationEnqueuer
import dev.russell.fatebook.data.sync.ResolvePayload
import dev.russell.fatebook.data.sync.SetSharedPayload
import dev.russell.fatebook.data.sync.SyncScheduler
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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuestionRepository @Inject constructor(
    private val api: FatebookApi,
    private val dao: QuestionDao,
    private val forecastDao: ForecastDao,
    private val commentDao: CommentDao,
    private val pendingDao: PendingMutationDao,
    private val prefs: UserPreferences,
    private val transactor: Transactor,
    private val enqueuer: MutationEnqueuer,
    private val syncScheduler: SyncScheduler,
) {
    private var nextCursor: Int? = null

    private val QuestionDto.isBinary: Boolean
        get() = questionType == null || questionType == "BINARY"

    /** Observe cached questions, mapped to domain models. */
    fun observeActive(): Flow<List<Question>> =
        dao.observeActive().map { entities -> entities.map { it.toDomain() } }

    fun observeReadyToResolve(): Flow<List<Question>> {
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
     * Questions not present in the response are pruned, EXCEPT locally-created ones
     * (id prefixed `local-`) that haven't synced yet.
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

    private suspend fun commitDtos(dtos: List<QuestionDto>, prune: Boolean) {
        val questionEntities = dtos.map { it.toEntity() }
        // Keep server ids from the response + any local-only ids that haven't synced.
        val localOnlyIds = dao.getAllIds()
            .filter { it.startsWith(PendingMutationEntity.LOCAL_ID_PREFIX) }
        val keepIds = dtos.map { it.id } + localOnlyIds
        transactor.transact {
            dao.upsertAll(questionEntities)
            if (prune) {
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

    // ---------- Optimistic mutations ----------
    //
    // Every mutation: apply the change to Room AND insert a PendingMutationEntity in
    // the same transaction. Then trigger SyncWorker. The caller's coroutine never
    // awaits the network — the queue does that later.

    suspend fun createQuestion(
        title: String,
        resolveBy: LocalDate,
        forecast: Double,
    ): String {
        val localId = PendingMutationEntity.LOCAL_ID_PREFIX + UUID.randomUUID()
        val resolveByMs = resolveBy.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val nowMs = System.currentTimeMillis()
        val question = QuestionEntity(
            id = localId,
            title = title,
            resolveByEpochMs = resolveByMs,
            createdAtEpochMs = nowMs,
            resolution = null,
            resolved = false,
            latestForecast = forecast,
            latestForecastAtEpochMs = nowMs,
            url = "",
            forecastHiddenUntilEpochMs = null,
            notes = null,
            sharedPublicly = false,
            unlisted = false,
        )
        transactor.transact {
            dao.upsertAll(listOf(question))
            forecastDao.upsertAll(
                listOf(
                    ForecastEntity(
                        questionId = localId,
                        forecast = forecast,
                        createdAtEpochMs = nowMs,
                    ),
                ),
            )
            enqueuer.enqueueCreate(
                questionLocalId = localId,
                payload = CreateQuestionPayload(
                    title = title,
                    resolveByEpochMs = resolveByMs,
                    forecast = forecast,
                    notes = null,
                ),
                createdAtEpochMs = nowMs,
            )
        }
        prefs.setLastPredictionDate(nowMs)
        syncScheduler.schedule()
        return localId
    }

    suspend fun addForecast(questionId: String, forecast: Double) {
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
            enqueuer.enqueueAddForecast(questionId, AddForecastPayload(forecast))
        }
        prefs.setLastPredictionDate(nowMs)
        syncScheduler.schedule()
    }

    suspend fun resolveQuestion(questionId: String, resolution: Resolution) {
        transactor.transact {
            dao.updateResolution(questionId, resolution.apiValue)
            enqueuer.enqueueResolve(questionId, ResolvePayload(resolution.apiValue))
        }
        syncScheduler.schedule()
    }

    suspend fun validateApiKey(): Boolean {
        return try {
            api.validateApiKey()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun editQuestion(
        questionId: String,
        title: String? = null,
        resolveBy: LocalDate? = null,
        notes: String? = null,
    ) {
        val resolveByMs = resolveBy?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        transactor.transact {
            dao.updateFields(
                questionId = questionId,
                title = title,
                resolveByEpochMs = resolveByMs,
                notes = notes,
                hasNotes = if (notes != null) 1 else 0,
            )
            enqueuer.enqueueEdit(
                questionId,
                EditPayload(title = title, resolveByEpochMs = resolveByMs, notes = notes),
            )
        }
        syncScheduler.schedule()
    }

    /**
     * Delete a question. If the question only exists locally (id prefixed `local-`)
     * AND its CREATE_QUESTION mutation hasn't synced yet, we collapse the whole
     * `[CREATE, …, DELETE]` chain — drop the local row, drop all queued mutations
     * for it, never bother the server.
     */
    suspend fun deleteQuestion(questionId: String) {
        val isLocal = questionId.startsWith(PendingMutationEntity.LOCAL_ID_PREFIX)
        transactor.transact {
            dao.deleteById(questionId)
            if (isLocal) {
                pendingDao.deleteByQuestion(questionId)
            } else {
                enqueuer.enqueueDelete(questionId)
            }
        }
        if (!isLocal) {
            syncScheduler.schedule()
        }
    }

    suspend fun getCommentsForQuestion(questionId: String): List<Comment> {
        return commentDao.getByQuestionId(questionId).map { it.toDomain() }
    }

    suspend fun addComment(questionId: String, comment: String): Comment {
        val now = Instant.now()
        val nowMs = now.toEpochMilli()
        val userName = prefs.displayName
        val localCommentId = "local-comment-${UUID.randomUUID()}"
        val entity = CommentEntity(
            id = localCommentId,
            questionId = questionId,
            userId = "",
            userName = userName,
            comment = comment,
            createdAtEpochMs = nowMs,
        )
        transactor.transact {
            commentDao.insert(entity)
            enqueuer.enqueueAddComment(questionId, AddCommentPayload(comment))
        }
        syncScheduler.schedule()
        return Comment(
            id = localCommentId,
            userId = "",
            userName = userName,
            comment = comment,
            createdAt = now,
        )
    }

    suspend fun setSharedPublicly(
        questionId: String,
        sharedPublicly: Boolean,
        unlisted: Boolean,
    ) {
        transactor.transact {
            dao.updateSharing(questionId, sharedPublicly, unlisted)
            enqueuer.enqueueSetShared(
                questionId,
                SetSharedPayload(sharedPublicly = sharedPublicly, unlisted = unlisted),
            )
        }
        syncScheduler.schedule()
    }

    // --- Sync-error UI helpers ---

    fun observePendingMutationCount(): Flow<Int> = pendingDao.observeErroredCount()

    fun observeErroredMutations(): Flow<List<PendingMutationEntity>> =
        pendingDao.observeErrored()

    suspend fun retryAllErroredMutations() {
        pendingDao.retryAllErrored()
        syncScheduler.schedule()
    }

    /**
     * Drop an errored mutation from the queue. For an errored CREATE_QUESTION,
     * if the server appears to already have a copy of the question, also drop
     * the local-id duplicate so the feed isn't permanently double-listed.
     */
    suspend fun discardErroredMutation(id: Long) {
        val mutation = pendingDao.getById(id)
        pendingDao.delete(id)
        if (mutation == null) return
        if (mutation.type != PendingMutationEntity.TYPE_CREATE_QUESTION) return
        if (!mutation.questionLocalId.startsWith(PendingMutationEntity.LOCAL_ID_PREFIX)) return

        val payload = enqueuer.decodeCreate(mutation.payloadJson)
        // 24h window for recovery — the user might be discarding hours after the failure.
        val serverCopy = dao.findCreatedNear(
            title = payload.title,
            aroundEpochMs = mutation.createdAtEpochMs,
            windowMs = 24L * 60 * 60 * 1000,
        )
        if (serverCopy != null) {
            dao.deleteById(mutation.questionLocalId)
        }
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
            forecasts = emptyList(),
            url = url,
            forecastHiddenUntil = forecastHiddenUntilEpochMs?.let { Instant.ofEpochMilli(it) },
            notes = notes,
            sharedPublicly = sharedPublicly,
            unlisted = unlisted,
        )
    }

    private fun parseInstant(dateStr: String): Instant {
        return try {
            Instant.parse(dateStr)
        } catch (_: Exception) {
            try {
                LocalDate.parse(dateStr)
                    .atStartOfDay(ZoneId.of("UTC"))
                    .toInstant()
            } catch (_: Exception) {
                Instant.EPOCH
            }
        }
    }
}
