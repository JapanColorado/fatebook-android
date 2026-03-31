package dev.russell.fatebook.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

data class Question(
    val id: String,
    val title: String,
    val resolveBy: Instant,
    val createdAt: Instant,
    val resolution: Resolution?,
    val resolved: Boolean,
    val yourLatestForecast: Double?,
    val latestForecastAt: Instant?,
    val forecasts: List<Forecast>,
    val url: String,
    val forecastHiddenUntil: Instant? = null,
    val notes: String? = null,
    val sharedPublicly: Boolean = false,
    val unlisted: Boolean = false,
    val comments: List<Comment> = emptyList(),
) {
    /** The resolve-by date (extracted from the UTC-midnight Instant the API stores). */
    val resolveByDate: LocalDate
        get() = resolveBy.atZone(ZoneOffset.UTC).toLocalDate()

    val isReadyToResolve: Boolean
        get() = !resolved && !LocalDate.now().isBefore(resolveByDate)

    val resolvesLabel: String
        get() = if (resolved || resolveByDate < LocalDate.now()) "Resolved" else "Resolves"

    val isForecastHidden: Boolean
        get() = forecastHiddenUntil != null && Instant.now().isBefore(forecastHiddenUntil)

    val forecastPercent: Int?
        get() = yourLatestForecast?.let { (it * 100).roundToInt() }
}

data class Forecast(
    val userId: String,
    val forecast: Double?,
    val createdAt: Instant,
)

data class Comment(
    val id: String,
    val userId: String,
    val userName: String?,
    val comment: String,
    val createdAt: Instant,
)
