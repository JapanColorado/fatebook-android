package dev.russell.fatebook.ui.components

import kotlin.math.roundToInt

/**
 * Pure math for the interactive probability pie chart. Values are slice
 * fractions in [0, 1] that always sum to exactly 1, so the options of an
 * exclusive multiple-choice question always add up to 100%.
 */
object PieChartMath {

    /** Smallest allowed slice — keeps every option grabbable and > 0%. */
    const val MIN_FRACTION = 0.01f

    /**
     * Editor starting values from the per-option latest forecasts. Options
     * without a forecast split whatever probability the forecast options
     * leave unclaimed; if nothing is forecast, all options split evenly.
     * The result is normalized to sum to 1 with every slice >= [MIN_FRACTION].
     */
    fun initialValues(forecasts: List<Double?>): List<Float> {
        val n = forecasts.size
        if (n == 0) return emptyList()
        val known = forecasts.filterNotNull()
        if (known.isEmpty()) return List(n) { 1f / n }.let(::clampToMin)

        val leftover = (1.0 - known.sum()).coerceAtLeast(0.0)
        val nullCount = n - known.size
        val fillEach = if (nullCount > 0) leftover / nullCount else 0.0
        val raw = forecasts.map { (it ?: fillEach).toFloat().coerceAtLeast(0f) }
        val sum = raw.sum()
        val normalized = if (sum > 0f) raw.map { it / sum } else List(n) { 1f / n }
        return clampToMin(normalized)
    }

    /**
     * Move the boundary between slice [boundary]-1 and slice [boundary]
     * (1-based over the gaps, so valid values are 1..n-1; the boundary at
     * 12 o'clock is fixed) to cumulative fraction [target]. Only the two
     * adjacent slices change; each is kept >= [MIN_FRACTION].
     */
    fun moveBoundary(values: List<Float>, boundary: Int, target: Float): List<Float> {
        if (boundary !in 1 until values.size) return values
        val cumulative = cumulative(values)
        val current = cumulative[boundary]
        // Unwrap a drag that crossed 12 o'clock so it clamps toward the
        // nearer limit instead of jumping to the far one.
        var t = target
        if (t - current > 0.5f) t -= 1f
        if (current - t > 0.5f) t += 1f
        t = t.coerceIn(cumulative[boundary - 1] + MIN_FRACTION, cumulative[boundary + 1] - MIN_FRACTION)

        val result = values.toMutableList()
        result[boundary - 1] = t - cumulative[boundary - 1]
        result[boundary] = cumulative[boundary + 1] - t
        return result
    }

    /** Cumulative boundary fractions: index 0 is always 0, index n is always 1. */
    fun cumulative(values: List<Float>): List<Float> {
        val out = ArrayList<Float>(values.size + 1)
        out.add(0f)
        var sum = 0f
        for (v in values) {
            sum += v
            out.add(sum)
        }
        out[values.size] = 1f
        return out
    }

    /** Whole percents that always sum to exactly 100 (largest remainder). */
    fun displayPercents(values: List<Float>): List<Int> {
        if (values.isEmpty()) return emptyList()
        val exact = values.map { it * 100f }
        val floors = exact.map { it.toInt() }.toMutableList()
        var remaining = 100 - floors.sum()
        val byRemainder = exact.indices.sortedByDescending { exact[it] - floors[it] }
        var i = 0
        while (remaining > 0 && i < byRemainder.size) {
            floors[byRemainder[i]] += 1
            remaining--
            i++
        }
        // Negative remainder can't happen (floors sum <= 100), but guard anyway.
        i = byRemainder.size - 1
        while (remaining < 0 && i >= 0) {
            val idx = byRemainder[i]
            if (floors[idx] > 0) {
                floors[idx] -= 1
                remaining++
            }
            i--
        }
        return floors
    }

    /** True when [a] and [b] round to different whole percents. */
    fun differsAsPercent(a: Double?, b: Double): Boolean {
        if (a == null) return true
        return (a * 100).roundToInt() != (b * 100).roundToInt()
    }

    private fun clampToMin(values: List<Float>): List<Float> {
        var current = values
        // Raising small slices to the floor shrinks the rest proportionally,
        // which can push another slice under the floor — iterate (bounded).
        repeat(values.size) {
            val below = current.count { it < MIN_FRACTION }
            if (below == 0) return current
            val floorTotal = below * MIN_FRACTION
            val restSum = current.filter { it >= MIN_FRACTION }.sum()
            current = current.map { v ->
                if (v < MIN_FRACTION) {
                    MIN_FRACTION
                } else if (restSum > 0f) {
                    v / restSum * (1f - floorTotal)
                } else {
                    (1f - floorTotal) / (current.size - below)
                }
            }
        }
        return current
    }
}
