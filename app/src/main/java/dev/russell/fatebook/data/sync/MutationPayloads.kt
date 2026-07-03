package dev.russell.fatebook.data.sync

import com.squareup.moshi.JsonClass

/**
 * Per-mutation payloads stored as JSON in [PendingMutationEntity.payloadJson].
 * Each is a standalone @JsonClass so Moshi codegen produces an adapter; the
 * SyncWorker picks the right adapter via the entity's `type` discriminator.
 */

@JsonClass(generateAdapter = true)
data class CreateQuestionPayload(
    val title: String,
    val resolveByEpochMs: Long,
    val forecast: Double,
    val notes: String?,
    val tags: List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class AddForecastPayload(
    val forecast: Double,
    // Required by the API for MULTIPLE_CHOICE questions, forbidden for BINARY.
    val optionId: String? = null,
)

@JsonClass(generateAdapter = true)
data class ResolvePayload(
    val resolution: String,
    val questionType: String = "BINARY",
    // Set only when resolving a single option of a non-exclusive MC question.
    val optionId: String? = null,
)

@JsonClass(generateAdapter = true)
data class EditPayload(
    val title: String?,
    val resolveByEpochMs: Long?,
    val notes: String?,
)

@JsonClass(generateAdapter = true)
data class SetSharedPayload(
    val sharedPublicly: Boolean,
    val unlisted: Boolean,
)

@JsonClass(generateAdapter = true)
data class AddCommentPayload(
    val comment: String,
)
