package dev.russell.fatebook.ui.components

import dev.russell.fatebook.domain.model.Forecast
import java.time.Instant

/** A point in unit space: x = 0..1 across time, y = 0..1 probability. */
data class SparklinePoint(val x: Float, val y: Float)

/** Pure helpers behind [ForecastSparkline]; no Compose dependencies so they unit-test directly. */
object SparklineMath {

    /**
     * The forecasts on a binary question that belong to the current user, oldest first.
     *
     * Local (not-yet-synced) forecasts have an empty userId and always count as
     * yours. When the user id is unknown, a question with a single forecaster is
     * assumed to be yours; a shared one is not.
     */
    fun yourForecasts(history: List<Forecast>, currentUserId: String?): List<Forecast> {
        val binary = history.filter { it.forecast != null && it.optionId == null }
        val distinctUsers = binary.map { it.userId }.filter { it.isNotEmpty() }.distinct()
        return binary
            .filter { f ->
                f.userId.isEmpty() ||
                    f.userId == currentUserId ||
                    (currentUserId == null && distinctUsers.size <= 1)
            }
            .sortedBy { it.createdAt }
    }

    /**
     * Map timestamped probabilities onto unit space. x spans [start, end]; a
     * degenerate span (end <= start) spaces the points evenly instead.
     */
    fun normalize(
        samples: List<Pair<Instant, Double>>,
        start: Instant,
        end: Instant,
    ): List<SparklinePoint> {
        if (samples.isEmpty()) return emptyList()
        val sorted = samples.sortedBy { it.first }
        val startMs = start.toEpochMilli()
        val span = (end.toEpochMilli() - startMs).toFloat()
        return sorted.mapIndexed { i, (at, value) ->
            val x = when {
                span <= 0f && sorted.size == 1 -> 0f
                span <= 0f -> i / (sorted.size - 1).toFloat()
                else -> ((at.toEpochMilli() - startMs) / span).coerceIn(0f, 1f)
            }
            SparklinePoint(x, value.toFloat().coerceIn(0f, 1f))
        }
    }

    /**
     * Expand points into a step-after polyline: each forecast holds until the
     * next one, and the last holds to x = 1.
     */
    fun stepPolyline(points: List<SparklinePoint>): List<SparklinePoint> {
        if (points.isEmpty()) return emptyList()
        val out = ArrayList<SparklinePoint>(points.size * 2 + 1)
        out += points.first()
        for (i in 1 until points.size) {
            out += SparklinePoint(points[i].x, points[i - 1].y)
            out += points[i]
        }
        if (points.last().x < 1f) out += SparklinePoint(1f, points.last().y)
        return out
    }
}
