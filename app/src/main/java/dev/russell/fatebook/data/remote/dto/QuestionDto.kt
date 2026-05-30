package dev.russell.fatebook.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QuestionsResponseDto(
    val items: List<QuestionDto>,
    val nextCursor: Int?,
)

@JsonClass(generateAdapter = true)
data class QuestionDto(
    val id: String,
    val title: String,
    val resolveBy: String, // ISO 8601
    @Json(name = "createdAt") val createdAt: String, // ISO 8601
    val resolution: String?, // YES / NO / AMBIGUOUS / null
    val resolved: Boolean,
    val resolvedAt: String? = null, // ISO 8601; when the question was resolved
    val url: String?,
    val forecasts: List<ForecastDto>?,
    val notes: String?,
    @Json(name = "type") val questionType: String? = null,
    val sharedPublicly: Boolean? = null,
    val unlisted: Boolean? = null,
    val comments: List<CommentDto>? = null,
)

@JsonClass(generateAdapter = true)
data class ForecastDto(
    val userId: String?,
    val forecast: Double?,
    @Json(name = "createdAt") val createdAt: String?,
    val hideForecastsUntil: String?,
    val user: UserDto? = null,
)

@JsonClass(generateAdapter = true)
data class CommentDto(
    val id: String?,
    val userId: String?,
    val comment: String?,
    @Json(name = "createdAt") val createdAt: String?,
    val user: UserDto? = null,
)

@JsonClass(generateAdapter = true)
data class UserDto(
    val name: String?,
)

@JsonClass(generateAdapter = true)
data class ApiErrorDto(
    val message: String?,
    val code: String?,
    val issues: List<ApiIssueDto>?,
)

@JsonClass(generateAdapter = true)
data class ApiIssueDto(
    val message: String?,
)
