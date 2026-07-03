package dev.russell.fatebook.testutil

import dev.russell.fatebook.data.local.OptionEntity
import dev.russell.fatebook.data.local.QuestionEntity
import dev.russell.fatebook.data.remote.dto.ForecastDto
import dev.russell.fatebook.data.remote.dto.OptionDto
import dev.russell.fatebook.data.remote.dto.QuestionDto
import dev.russell.fatebook.data.remote.dto.QuestionsResponseDto
import dev.russell.fatebook.data.remote.dto.TagDto
import dev.russell.fatebook.domain.model.Forecast
import dev.russell.fatebook.domain.model.Question
import dev.russell.fatebook.domain.model.QuestionOption
import dev.russell.fatebook.domain.model.QuestionType
import dev.russell.fatebook.domain.model.Resolution
import java.time.Instant

object TestData {

    fun question(
        id: String = "q1",
        title: String = "Will it rain tomorrow?",
        resolveBy: Instant = Instant.parse("2030-06-01T00:00:00Z"),
        createdAt: Instant = Instant.parse("2020-01-01T00:00:00Z"),
        resolution: Resolution? = null,
        resolved: Boolean = false,
        resolvedAt: Instant? = null,
        yourLatestForecast: Double? = 0.7,
        latestForecastAt: Instant? = Instant.parse("2020-01-02T00:00:00Z"),
        forecasts: List<Forecast> = emptyList(),
        url: String = "https://fatebook.io/q/$id",
        forecastHiddenUntil: Instant? = null,
        type: QuestionType = QuestionType.BINARY,
        exclusiveAnswers: Boolean = true,
        options: List<QuestionOption> = emptyList(),
        tags: List<String> = emptyList(),
    ) = Question(
        id = id,
        title = title,
        resolveBy = resolveBy,
        createdAt = createdAt,
        resolution = resolution,
        resolved = resolved,
        resolvedAt = resolvedAt,
        yourLatestForecast = yourLatestForecast,
        latestForecastAt = latestForecastAt,
        forecasts = forecasts,
        url = url,
        forecastHiddenUntil = forecastHiddenUntil,
        type = type,
        exclusiveAnswers = exclusiveAnswers,
        options = options,
        tags = tags,
    )

    fun questionOption(
        id: String = "opt1",
        text: String = "Option A",
        latestForecast: Double? = 0.4,
        latestForecastAt: Instant? = Instant.parse("2020-01-02T00:00:00Z"),
        resolution: Resolution? = null,
        resolvedAt: Instant? = null,
    ) = QuestionOption(
        id = id,
        text = text,
        latestForecast = latestForecast,
        latestForecastAt = latestForecastAt,
        resolution = resolution,
        resolvedAt = resolvedAt,
    )

    fun questionDto(
        id: String = "q1",
        title: String = "Will it rain tomorrow?",
        resolveBy: String = "2030-06-01T00:00:00Z",
        createdAt: String = "2020-01-01T00:00:00Z",
        resolution: String? = null,
        resolved: Boolean = false,
        resolvedAt: String? = null,
        url: String? = "https://fatebook.io/q/$id",
        forecasts: List<ForecastDto>? = listOf(
            ForecastDto(
                userId = "user1",
                forecast = 0.7,
                createdAt = "2020-01-02T00:00:00Z",
                hideForecastsUntil = null,
            )
        ),
        notes: String? = null,
        questionType: String? = "BINARY",
        options: List<OptionDto>? = null,
        tags: List<TagDto>? = null,
        exclusiveAnswers: Boolean? = null,
    ) = QuestionDto(
        id = id,
        title = title,
        resolveBy = resolveBy,
        createdAt = createdAt,
        resolution = resolution,
        resolved = resolved,
        resolvedAt = resolvedAt,
        url = url,
        forecasts = forecasts,
        notes = notes,
        questionType = questionType,
        options = options,
        tags = tags,
        exclusiveAnswers = exclusiveAnswers,
    )

    fun optionDto(
        id: String = "opt1",
        text: String = "Option A",
        createdAt: String? = "2020-01-01T00:00:00Z",
        resolution: String? = null,
        resolvedAt: String? = null,
    ) = OptionDto(
        id = id,
        text = text,
        createdAt = createdAt,
        resolution = resolution,
        resolvedAt = resolvedAt,
    )

    fun questionEntity(
        id: String = "q1",
        title: String = "Will it rain tomorrow?",
        resolveByEpochMs: Long = Instant.parse("2030-06-01T00:00:00Z").toEpochMilli(),
        createdAtEpochMs: Long = Instant.parse("2020-01-01T00:00:00Z").toEpochMilli(),
        resolution: String? = null,
        resolved: Boolean = false,
        resolvedAtEpochMs: Long? = null,
        latestForecast: Double? = 0.7,
        latestForecastAtEpochMs: Long? = Instant.parse("2020-01-02T00:00:00Z").toEpochMilli(),
        url: String = "https://fatebook.io/q/$id",
        forecastHiddenUntilEpochMs: Long? = null,
        questionType: String = "BINARY",
        exclusiveAnswers: Boolean = true,
        tagsJson: String = "[]",
    ) = QuestionEntity(
        id = id,
        title = title,
        resolveByEpochMs = resolveByEpochMs,
        createdAtEpochMs = createdAtEpochMs,
        resolution = resolution,
        resolved = resolved,
        resolvedAtEpochMs = resolvedAtEpochMs,
        latestForecast = latestForecast,
        latestForecastAtEpochMs = latestForecastAtEpochMs,
        url = url,
        forecastHiddenUntilEpochMs = forecastHiddenUntilEpochMs,
        questionType = questionType,
        exclusiveAnswers = exclusiveAnswers,
        tagsJson = tagsJson,
    )

    fun optionEntity(
        id: String = "opt1",
        questionId: String = "q1",
        text: String = "Option A",
        createdAtEpochMs: Long = Instant.parse("2020-01-01T00:00:00Z").toEpochMilli(),
        resolution: String? = null,
        resolvedAtEpochMs: Long? = null,
        latestForecast: Double? = 0.4,
        latestForecastAtEpochMs: Long? = Instant.parse("2020-01-02T00:00:00Z").toEpochMilli(),
    ) = OptionEntity(
        id = id,
        questionId = questionId,
        text = text,
        createdAtEpochMs = createdAtEpochMs,
        resolution = resolution,
        resolvedAtEpochMs = resolvedAtEpochMs,
        latestForecast = latestForecast,
        latestForecastAtEpochMs = latestForecastAtEpochMs,
    )

    fun questionsResponse(
        items: List<QuestionDto> = listOf(questionDto()),
        nextCursor: Int? = null,
    ) = QuestionsResponseDto(items = items, nextCursor = nextCursor)
}
