package dev.russell.fatebook.domain.model

import androidx.compose.runtime.Immutable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

enum class QuestionType {
    BINARY,
    MULTIPLE_CHOICE,
    QUANTITY,
    ;

    companion object {
        fun fromApi(value: String?): QuestionType =
            entries.firstOrNull { it.name == value } ?: BINARY
    }
}

@Immutable
data class Question(
    val id: String,
    val title: String,
    val resolveBy: Instant,
    val createdAt: Instant,
    val resolution: Resolution?,
    val resolved: Boolean,
    val resolvedAt: Instant? = null,
    val yourLatestForecast: Double?,
    val latestForecastAt: Instant?,
    val forecasts: List<Forecast>,
    val url: String,
    val forecastHiddenUntil: Instant? = null,
    val notes: String? = null,
    val sharedPublicly: Boolean = false,
    val unlisted: Boolean = false,
    val comments: List<Comment> = emptyList(),
    val type: QuestionType = QuestionType.BINARY,
    val exclusiveAnswers: Boolean = true,
    val options: List<QuestionOption> = emptyList(),
    val tags: List<String> = emptyList(),
) {
    /** The resolve-by date (extracted from the UTC-midnight Instant the API stores). */
    val resolveByDate: LocalDate
        get() = resolveBy.atZone(ZoneOffset.UTC).toLocalDate()

    val isReadyToResolve: Boolean
        get() = !resolved && !LocalDate.now().isBefore(resolveByDate)

    val resolvesLabel: String
        get() = if (resolved) "Resolved" else "Resolves"

    val isForecastHidden: Boolean
        get() = forecastHiddenUntil != null && Instant.now().isBefore(forecastHiddenUntil)

    val forecastPercent: Int?
        get() = yourLatestForecast?.let { (it * 100).roundToInt() }

    /** The YES-resolved option of a multiple-choice question — its "winner", if any. */
    val winningOption: QuestionOption?
        get() = options.firstOrNull { it.resolution == Resolution.YES }

    /**
     * Exclusive multiple-choice questions that are still open for forecasting use
     * the interactive pie editor (options must sum to 100%). Non-exclusive MC
     * options are independent probabilities, so they keep per-option sliders.
     */
    val isPieEditable: Boolean
        get() = type == QuestionType.MULTIPLE_CHOICE &&
            exclusiveAnswers &&
            options.size >= 2 &&
            options.all { it.resolution == null } &&
            !resolved && !isReadyToResolve && !isForecastHidden
}

@Immutable
data class QuestionOption(
    val id: String,
    val text: String,
    val latestForecast: Double? = null,
    val latestForecastAt: Instant? = null,
    val resolution: Resolution? = null,
    val resolvedAt: Instant? = null,
) {
    val forecastPercent: Int?
        get() = latestForecast?.let { (it * 100).roundToInt() }
}

@Immutable
data class Forecast(
    val userId: String,
    val forecast: Double?,
    val createdAt: Instant,
    val userName: String? = null,
    val optionId: String? = null,
)

@Immutable
data class Comment(
    val id: String,
    val userId: String,
    val userName: String?,
    val comment: String,
    val createdAt: Instant,
)
