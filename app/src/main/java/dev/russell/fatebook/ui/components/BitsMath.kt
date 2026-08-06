package dev.russell.fatebook.ui.components

import kotlin.math.pow

/**
 * Log-odds ("bits of evidence") math for binary probabilities. One bit of
 * evidence doubles the odds p/(1-p): 50% + 1 bit = 67%, 75% + 1 bit = 86%.
 */
object BitsMath {

    /** Bounds match ProbabilitySlider's range — odds degenerate at 0% and 100%. */
    const val MIN_PROBABILITY = 0.01f
    const val MAX_PROBABILITY = 0.99f

    /**
     * Applies [bits] bits of evidence in log-odds space: each +1 bit doubles
     * the odds, each -1 bit halves them. Both the input and the result are
     * clamped to [MIN_PROBABILITY]..[MAX_PROBABILITY], so callers can never
     * produce a value outside the slider's valid range.
     */
    fun applyBits(probability: Float, bits: Int): Float {
        val p = probability.coerceIn(MIN_PROBABILITY, MAX_PROBABILITY).toDouble()
        val odds = p / (1.0 - p) * 2.0.pow(bits)
        return (odds / (1.0 + odds)).toFloat().coerceIn(MIN_PROBABILITY, MAX_PROBABILITY)
    }
}
