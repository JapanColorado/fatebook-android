package dev.russell.fatebook.testutil

import dev.russell.fatebook.data.local.QuestionEntity
import dev.russell.fatebook.data.remote.dto.ForecastDto
import dev.russell.fatebook.data.remote.dto.QuestionDto
import dev.russell.fatebook.data.remote.dto.QuestionsResponseDto
import dev.russell.fatebook.domain.model.Forecast
import dev.russell.fatebook.domain.model.Question
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
        yourLatestForecast: Double? = 0.7,
        latestForecastAt: Instant? = Instant.parse("2020-01-02T00:00:00Z"),
        forecasts: List<Forecast> = emptyList(),
        url: String = "https://fatebook.io/q/$id",
        forecastHiddenUntil: Instant? = null,
    ) = Question(
        id = id,
        title = title,
        resolveBy = resolveBy,
        createdAt = createdAt,
        resolution = resolution,
        resolved = resolved,
        yourLatestForecast = yourLatestForecast,
        latestForecastAt = latestForecastAt,
        forecasts = forecasts,
        url = url,
        forecastHiddenUntil = forecastHiddenUntil,
    )

    fun questionDto(
        id: String = "q1",
        title: String = "Will it rain tomorrow?",
        resolveBy: String = "2030-06-01T00:00:00Z",
        createdAt: String = "2020-01-01T00:00:00Z",
        resolution: String? = null,
        resolved: Boolean = false,
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
    ) = QuestionDto(
        id = id,
        title = title,
        resolveBy = resolveBy,
        createdAt = createdAt,
        resolution = resolution,
        resolved = resolved,
        url = url,
        forecasts = forecasts,
        notes = notes,
        questionType = questionType,
    )

    fun questionEntity(
        id: String = "q1",
        title: String = "Will it rain tomorrow?",
        resolveByEpochMs: Long = Instant.parse("2030-06-01T00:00:00Z").toEpochMilli(),
        createdAtEpochMs: Long = Instant.parse("2020-01-01T00:00:00Z").toEpochMilli(),
        resolution: String? = null,
        resolved: Boolean = false,
        latestForecast: Double? = 0.7,
        latestForecastAtEpochMs: Long? = Instant.parse("2020-01-02T00:00:00Z").toEpochMilli(),
        url: String = "https://fatebook.io/q/$id",
        forecastHiddenUntilEpochMs: Long? = null,
    ) = QuestionEntity(
        id = id,
        title = title,
        resolveByEpochMs = resolveByEpochMs,
        createdAtEpochMs = createdAtEpochMs,
        resolution = resolution,
        resolved = resolved,
        latestForecast = latestForecast,
        latestForecastAtEpochMs = latestForecastAtEpochMs,
        url = url,
        forecastHiddenUntilEpochMs = forecastHiddenUntilEpochMs,
    )

    fun questionsResponse(
        items: List<QuestionDto> = listOf(questionDto()),
        nextCursor: Int? = null,
    ) = QuestionsResponseDto(items = items, nextCursor = nextCursor)
}
