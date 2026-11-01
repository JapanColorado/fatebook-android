package dev.russell.fatebook.ui.components

import dev.russell.fatebook.domain.model.QuestionOption
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Pure math for the multiple-choice forecast editor. Drafts are whole percents
 * keyed by option id; an option with no entry has no forecast yet.
 */
object OptionForecastMath {

    private const val WHOLE = 100
    private const val MIN_PERCENT = 1
    private const val MAX_PERCENT = 99

    /** Editor starting values: every unresolved option's current forecast. */
    fun initialDrafts(options: List<QuestionOption>): Map<String, Int> = options
        .filter { it.resolution == null }
        .mapNotNull { option -> option.forecastPercent?.let { option.id to it } }
        .toMap()

    /** Percent [bits] bits of evidence from [percent], within the ruler's range. */
    fun applyBits(percent: Int, bits: Int): Int =
        (BitsMath.applyBits(percent / 100f, bits) * 100).roundToInt().coerceIn(MIN_PERCENT, MAX_PERCENT)

    /** The options whose draft would actually change their forecast, with that draft. */
    fun changes(options: List<QuestionOption>, drafts: Map<String, Int>): List<Pair<QuestionOption, Int>> =
        options.mapNotNull { option ->
            drafts[option.id]?.takeIf { it != option.forecastPercent }?.let { option to it }
        }

    /** What an exclusive question's options leave for its Other outcome. */
    fun leftForOther(drafts: Map<String, Int>): Int = (WHOLE - drafts.values.sum()).coerceAtLeast(0)

    /** How far an exclusive question's options exceed 100%. */
    fun overBy(drafts: Map<String, Int>): Int = (drafts.values.sum() - WHOLE).coerceAtLeast(0)

    /**
     * Scale over-allocated drafts down to exactly 100%, keeping their proportions
     * as closely as whole percents allow (largest remainder) and every option
     * at [MIN_PERCENT] or more. Drafts within 100% come back unchanged.
     */
    fun balance(drafts: Map<String, Int>): Map<String, Int> {
        val total = drafts.values.sum()
        if (total <= WHOLE) return drafts

        val exact = drafts.mapValues { (_, percent) -> percent * WHOLE.toDouble() / total }
        val result = exact.mapValues { (_, share) -> floor(share).toInt().coerceAtLeast(MIN_PERCENT) }.toMutableMap()
        var unassigned = WHOLE - result.values.sum()

        val byRemainder = exact.keys.sortedByDescending { id -> exact.getValue(id) - floor(exact.getValue(id)) }
        for (id in byRemainder) {
            if (unassigned <= 0) break
            result[id] = result.getValue(id) + 1
            unassigned--
        }
        // Raising tiny shares to the minimum can overshoot: take it back from the largest.
        while (unassigned < 0) {
            val largest = result.entries.maxBy { it.value }
            if (largest.value <= MIN_PERCENT) break
            result[largest.key] = largest.value - 1
            unassigned++
        }
        return result
    }
}
