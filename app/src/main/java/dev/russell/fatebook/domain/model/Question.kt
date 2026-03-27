package dev.russell.fatebook.domain.model

import java.time.Instant
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
) {
    val isReadyToResolve: Boolean
        get() = !resolved && !Instant.now().isBefore(resolveBy)

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
