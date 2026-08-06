package dev.russell.fatebook.ui.analytics

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

/**
 * Faithful port of Fatebook's Brier-score algorithm (`Sage-Future/fatebook`,
 * `lib/_scoring.ts` + `lib/_utils_common.ts`). This matches the number the
 * fatebook.io website shows, which differs from a naive Brier average in two
 * important ways:
 *
 *  1. **Two-sided ("multi-class") Brier score.** Fatebook scores *both* outcomes:
 *     `(f - t)^2 + ((1-f) - (1-t))^2`, which for a binary question equals
 *     `2 * (f - t)^2`. So Fatebook's score is exactly twice the common one-sided
 *     Brier score, and an always-50% forecaster scores 0.5 (not 0.25).
 *
 *  2. **Per-question, time-weighted averaging.** Within a question, each day from
 *     creation to resolution contributes equally; the forecast used for a day is
 *     the time-weighted average of the forecasts active during it. The overall
 *     score is the unweighted mean of the per-question scores, so a question you
 *     updated 20 times counts the same as one you forecasted once.
 *
 * Only YES/NO resolutions are scored (AMBIGUOUS/unresolved questions are excluded
 * by the caller). Forecasts are treated as belonging to a single user ("you").
 */
object BrierScoring {

    private const val DAY_MS: Long = 1000L * 60 * 60 * 24
    private const val FLOAT_TOLERANCE = 0.0001

    data class TimedForecast(val createdAtMs: Long, val forecast: Double)

    data class QuestionForScoring(
        val createdAtMs: Long,
        val resolvedAtMs: Long,
        val resolvedYes: Boolean,
        /** This user's forecasts on the question; order does not matter. */
        val forecasts: List<TimedForecast>,
    )

    /**
     * Two-sided Brier score for a single forecast against a binary outcome.
     * Range 0.0 (perfect) .. 2.0 (maximally wrong). 0.5 forecast → 0.5.
     */
    fun brierScore(forecast: Double, resolvedYes: Boolean): Double {
        val trueValue = if (resolvedYes) 1.0 else 0.0
        return (forecast - trueValue).pow(2) + ((1 - forecast) - (1 - trueValue)).pow(2)
    }

    /**
     * Overall absolute Brier score across questions: the mean of each question's
     * time-weighted score, weighting every (scored) question equally. Questions
     * with no forecast before resolution contribute nothing. Returns null when no
     * question can be scored.
     */
    fun overallBrierScore(questions: List<QuestionForScoring>): Double? =
        meanOrNull(questions.mapNotNull { questionBrierScore(it) })

    /**
     * The aggregation rule: unweighted mean of per-item scores, null when
     * nothing could be scored. Also used by AnalyticsViewModel, which averages
     * lazily-cached per-item scores instead of recomputing them per grouping.
     */
    fun meanOrNull(scores: List<Double>): Double? =
        if (scores.isEmpty()) null else scores.sum() / scores.size

    /**
     * Time-weighted absolute Brier score for a single question. Returns null if the
     * user had no forecast active during the question's lifetime.
     */
    fun questionBrierScore(question: QuestionForScoring): Double? {
        val forecasts = question.forecasts.sortedBy { it.createdAtMs }
        if (forecasts.isEmpty()) return null

        val createdAt = question.createdAtMs
        val resolvedAt = question.resolvedAtMs
        val days = (resolvedAt - createdAt).toDouble() / DAY_MS

        // Degenerate window (resolved at or before creation): fall back to scoring
        // the most recent forecast at/before resolution. Fatebook's day loop would
        // produce no intervals here; this keeps a sensible value instead of 0.
        if (days <= 0.0) {
            val latest = forecasts.lastOrNull { it.createdAtMs <= resolvedAt }
                ?: forecasts.first()
            return brierScore(latest.forecast, question.resolvedYes)
        }

        val fractionalDay = days - floor(days)
        val endDay = createdAt + ceil(days).toLong() * DAY_MS

        // One absolute Brier score per (full or fractional) day with a live forecast.
        val dailyScores = mutableListOf<Double>()
        var j = createdAt
        while (j < endDay) {
            val currentInterval = if (j + DAY_MS < resolvedAt) j + DAY_MS else resolvedAt
            val startOfInterval = if (j < resolvedAt) j else resolvedAt
            val intervalLen = currentInterval - startOfInterval

            val mostRecent = forecasts.lastOrNull { it.createdAtMs <= startOfInterval }?.forecast
            val inInterval = forecasts.filter {
                it.createdAtMs in startOfInterval until currentInterval
            }

            val avg = weightedAverageForInterval(mostRecent, inInterval, startOfInterval, intervalLen)
            if (avg != null) {
                dailyScores.add(brierScore(avg, question.resolvedYes))
            }
            j += DAY_MS
        }

        // No interval had a live forecast (e.g. only forecasted after resolution).
        if (dailyScores.isEmpty()) return null

        return averageOverDays(dailyScores, floor(days).toInt(), fractionalDay)
    }

    /**
     * Time-weighted average of the forecasts in effect during one interval, weighted
     * by the fraction of the interval each forecast was the latest. Returns null when
     * no forecast was ever made on or before this interval. Mirrors Fatebook's
     * `getWeightedAverageForecastOfInterval`.
     */
    private fun weightedAverageForInterval(
        mostRecent: Double?,
        inInterval: List<TimedForecast>,
        startOfInterval: Long,
        intervalLen: Long,
    ): Double? {
        val len = intervalLen.toDouble()
        var avg = 0.0
        var sumOfWeights = 0.0

        when {
            mostRecent != null && inInterval.isNotEmpty() -> {
                // Carried-over forecast held until the first in-interval update.
                val weight = (inInterval.first().createdAtMs - startOfInterval).toDouble() / len
                sumOfWeights += weight
                avg += weight * mostRecent
            }
            mostRecent != null -> return mostRecent // no updates this interval
            inInterval.isEmpty() -> return null // never forecasted yet
            // else: first-ever forecast lands mid-interval; weights below cover it.
        }

        for (i in 0 until inInterval.size - 1) {
            val thisF = inInterval[i]
            val nextF = inInterval[i + 1]
            val weight = (nextF.createdAtMs - thisF.createdAtMs).toDouble() / len
            sumOfWeights += weight
            avg += weight * thisF.forecast
        }
        val lastWeight = 1.0 - sumOfWeights
        avg += lastWeight * inInterval.last().forecast
        return avg
    }

    /**
     * Average the per-day scores, weighting the final (fractional) day by its
     * fraction so every whole day counts as 1. Mirrors `averageForScoreResolution`.
     */
    private fun averageOverDays(
        scores: List<Double>,
        wholeDays: Int,
        fractionalDay: Double,
    ): Double {
        if (scores.isEmpty()) return 0.0
        return when {
            wholeDays == 0 -> scores.sum() // window shorter than a day
            abs(fractionalDay) >= FLOAT_TOLERANCE -> {
                val sum = scores.dropLast(1).sum() + scores.last() * fractionalDay
                sum / (wholeDays + fractionalDay)
            }
            else -> scores.sum() / wholeDays
        }
    }
}
