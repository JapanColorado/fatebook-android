package dev.russell.fatebook.data.sync

import com.squareup.moshi.Moshi
import dev.russell.fatebook.data.local.PendingMutationDao
import dev.russell.fatebook.data.local.PendingMutationEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Type-safe wrapper around [PendingMutationDao] that serializes payloads via
 * Moshi. Each `enqueue*` builds the entity and inserts it — callers should run
 * the call inside the same Room transaction as the optimistic local update so
 * the queue and the visible state never disagree.
 */
@Singleton
class MutationEnqueuer @Inject constructor(
    private val dao: PendingMutationDao,
    moshi: Moshi,
) {
    private val createAdapter = moshi.adapter(CreateQuestionPayload::class.java)
    private val forecastAdapter = moshi.adapter(AddForecastPayload::class.java)
    private val resolveAdapter = moshi.adapter(ResolvePayload::class.java)
    private val editAdapter = moshi.adapter(EditPayload::class.java)
    private val sharedAdapter = moshi.adapter(SetSharedPayload::class.java)
    private val commentAdapter = moshi.adapter(AddCommentPayload::class.java)

    suspend fun enqueueCreate(
        questionLocalId: String,
        payload: CreateQuestionPayload,
        createdAtEpochMs: Long = System.currentTimeMillis(),
    ): Long = dao.insert(
        PendingMutationEntity(
            type = PendingMutationEntity.TYPE_CREATE_QUESTION,
            questionLocalId = questionLocalId,
            payloadJson = createAdapter.toJson(payload),
            createdAtEpochMs = createdAtEpochMs,
        ),
    )

    suspend fun enqueueAddForecast(
        questionLocalId: String,
        payload: AddForecastPayload,
    ): Long = dao.insert(
        PendingMutationEntity(
            type = PendingMutationEntity.TYPE_ADD_FORECAST,
            questionLocalId = questionLocalId,
            payloadJson = forecastAdapter.toJson(payload),
            createdAtEpochMs = System.currentTimeMillis(),
        ),
    )

    suspend fun enqueueResolve(
        questionLocalId: String,
        payload: ResolvePayload,
    ): Long = dao.insert(
        PendingMutationEntity(
            type = PendingMutationEntity.TYPE_RESOLVE,
            questionLocalId = questionLocalId,
            payloadJson = resolveAdapter.toJson(payload),
            createdAtEpochMs = System.currentTimeMillis(),
        ),
    )

    suspend fun enqueueEdit(
        questionLocalId: String,
        payload: EditPayload,
    ): Long = dao.insert(
        PendingMutationEntity(
            type = PendingMutationEntity.TYPE_EDIT,
            questionLocalId = questionLocalId,
            payloadJson = editAdapter.toJson(payload),
            createdAtEpochMs = System.currentTimeMillis(),
        ),
    )

    suspend fun enqueueDelete(questionLocalId: String): Long = dao.insert(
        PendingMutationEntity(
            type = PendingMutationEntity.TYPE_DELETE,
            questionLocalId = questionLocalId,
            payloadJson = "{}",
            createdAtEpochMs = System.currentTimeMillis(),
        ),
    )

    suspend fun enqueueSetShared(
        questionLocalId: String,
        payload: SetSharedPayload,
    ): Long = dao.insert(
        PendingMutationEntity(
            type = PendingMutationEntity.TYPE_SET_SHARED,
            questionLocalId = questionLocalId,
            payloadJson = sharedAdapter.toJson(payload),
            createdAtEpochMs = System.currentTimeMillis(),
        ),
    )

    suspend fun enqueueAddComment(
        questionLocalId: String,
        payload: AddCommentPayload,
    ): Long = dao.insert(
        PendingMutationEntity(
            type = PendingMutationEntity.TYPE_ADD_COMMENT,
            questionLocalId = questionLocalId,
            payloadJson = commentAdapter.toJson(payload),
            createdAtEpochMs = System.currentTimeMillis(),
        ),
    )

    fun decodeCreate(json: String): CreateQuestionPayload = createAdapter.fromJson(json)!!
    fun decodeForecast(json: String): AddForecastPayload = forecastAdapter.fromJson(json)!!
    fun decodeResolve(json: String): ResolvePayload = resolveAdapter.fromJson(json)!!
    fun decodeEdit(json: String): EditPayload = editAdapter.fromJson(json)!!
    fun decodeSetShared(json: String): SetSharedPayload = sharedAdapter.fromJson(json)!!
    fun decodeAddComment(json: String): AddCommentPayload = commentAdapter.fromJson(json)!!
}
