package dev.russell.fatebook.ui.analytics

import com.google.common.truth.Truth.assertThat
import dev.russell.fatebook.ui.analytics.BrierScoring.QuestionForScoring
import dev.russell.fatebook.ui.analytics.BrierScoring.TimedForecast
import org.junit.Test

/**
 * Unit tests for [BrierScoring], the faithful port of fatebook.io's Brier-score
 * algorithm. Expected values are hand-computed in the comments so the math is
 * auditable independent of the implementation.
 */
class BrierScoringTest {

    private val day = 1000L * 60 * 60 * 24

    private fun fc(atDays: Double, value: Double) =
        TimedForecast(createdAtMs = (atDays * day).toLong(), forecast = value)

    private fun question(
        createdAtDays: Double = 0.0,
        resolvedAtDays: Double,
        resolvedYes: Boolean,
        forecasts: List<TimedForecast>,
    ) = QuestionForScoring(
        createdAtMs = (createdAtDays * day).toLong(),
        resolvedAtMs = (resolvedAtDays * day).toLong(),
        resolvedYes = resolvedYes,
        forecasts = forecasts,
    )

    // --- brierScore (two-sided / Fatebook definition) ---

    @Test
    fun `two-sided brier is twice the one-sided value`() {
        // (0.8-1)^2 + ((1-0.8)-0)^2 = 0.04 + 0.04 = 0.08
        assertThat(BrierScoring.brierScore(0.8, resolvedYes = true)).isWithin(1e-9).of(0.08)
    }

    @Test
    fun `brier for NO outcome`() {
        // (0.3-0)^2 + ((0.7)-(1))^2 = 0.09 + 0.09 = 0.18
        assertThat(BrierScoring.brierScore(0.3, resolvedYes = false)).isWithin(1e-9).of(0.18)
    }

    @Test
    fun `fifty percent always scores one half regardless of outcome`() {
        assertThat(BrierScoring.brierScore(0.5, resolvedYes = true)).isWithin(1e-9).of(0.5)
        assertThat(BrierScoring.brierScore(0.5, resolvedYes = false)).isWithin(1e-9).of(0.5)
    }

    @Test
    fun `perfect and maximally wrong forecasts`() {
        assertThat(BrierScoring.brierScore(1.0, resolvedYes = true)).isWithin(1e-9).of(0.0)
        assertThat(BrierScoring.brierScore(0.0, resolvedYes = false)).isWithin(1e-9).of(0.0)
        assertThat(BrierScoring.brierScore(1.0, resolvedYes = false)).isWithin(1e-9).of(2.0)
        assertThat(BrierScoring.brierScore(0.0, resolvedYes = true)).isWithin(1e-9).of(2.0)
    }

    // --- questionBrierScore: single constant forecast ---

    @Test
    fun `single constant forecast over whole life equals plain two-sided brier`() {
        // 0.8 held the entire 10 days, resolved YES -> 0.08 every day -> 0.08.
        val q = question(
            resolvedAtDays = 10.0,
            resolvedYes = true,
            forecasts = listOf(fc(0.0, 0.8)),
        )
        assertThat(BrierScoring.questionBrierScore(q)).isWithin(1e-9).of(0.08)
    }

    @Test
    fun `forecast made before question creation still counts`() {
        // Forecast predates createdAt; it is the "most recent" forecast for every
        // interval, so it scores as if held the whole time -> 0.08.
        val q = question(
            createdAtDays = 0.0,
            resolvedAtDays = 10.0,
            resolvedYes = true,
            forecasts = listOf(fc(-5.0, 0.8)),
        )
        assertThat(BrierScoring.questionBrierScore(q)).isWithin(1e-9).of(0.08)
    }

    // --- questionBrierScore: time-weighting across days ---

    @Test
    fun `forecast updated halfway is time-weighted equally across the two halves`() {
        // 0.6 for days 0-5, then 0.9 for days 5-10, resolved YES.
        // brier(0.6,YES)=0.32 (x5 days), brier(0.9,YES)=0.02 (x5 days)
        // (5*0.32 + 5*0.02) / 10 = 1.7 / 10 = 0.17
        val q = question(
            resolvedAtDays = 10.0,
            resolvedYes = true,
            forecasts = listOf(fc(0.0, 0.6), fc(5.0, 0.9)),
        )
        assertThat(BrierScoring.questionBrierScore(q)).isWithin(1e-9).of(0.17)
    }

    @Test
    fun `sub-day updates are weighted by the fraction of the day they were held`() {
        // 1-day question: 0.8 for the first quarter day, 0.4 for the last 3/4.
        // weighted avg forecast = 0.25*0.8 + 0.75*0.4 = 0.2 + 0.3 = 0.5
        // brier(0.5, YES) = 0.5
        val q = question(
            resolvedAtDays = 1.0,
            resolvedYes = true,
            forecasts = listOf(fc(0.0, 0.8), fc(0.25, 0.4)),
        )
        assertThat(BrierScoring.questionBrierScore(q)).isWithin(1e-9).of(0.5)
    }

    @Test
    fun `fractional last day is weighted by its fraction`() {
        // 1.5-day question: 0.9 for day 0-1, 0.1 for the last half day, resolved YES.
        // brier(0.9,YES)=0.02 (full day), brier(0.1,YES)=1.62 (half day)
        // (0.02 + 1.62*0.5) / (1 + 0.5) = (0.02 + 0.81) / 1.5 = 0.83 / 1.5
        val q = question(
            resolvedAtDays = 1.5,
            resolvedYes = true,
            forecasts = listOf(fc(0.0, 0.9), fc(1.0, 0.1)),
        )
        assertThat(BrierScoring.questionBrierScore(q)).isWithin(1e-9).of(0.83 / 1.5)
    }

    // --- questionBrierScore: edge cases ---

    @Test
    fun `no forecasts returns null`() {
        val q = question(resolvedAtDays = 10.0, resolvedYes = true, forecasts = emptyList())
        assertThat(BrierScoring.questionBrierScore(q)).isNull()
    }

    @Test
    fun `forecast only after resolution returns null`() {
        val q = question(
            resolvedAtDays = 10.0,
            resolvedYes = true,
            forecasts = listOf(fc(20.0, 0.8)),
        )
        assertThat(BrierScoring.questionBrierScore(q)).isNull()
    }

    @Test
    fun `resolved at creation falls back to the latest forecast`() {
        // Degenerate zero-length window: score the most recent forecast at/before resolution.
        val q = question(
            createdAtDays = 0.0,
            resolvedAtDays = 0.0,
            resolvedYes = true,
            forecasts = listOf(fc(-1.0, 0.7), fc(-0.5, 0.9)),
        )
        // brier(0.9, YES) = 0.02
        assertThat(BrierScoring.questionBrierScore(q)).isWithin(1e-9).of(0.02)
    }

    // --- overallBrierScore: per-question equal weighting ---

    @Test
    fun `overall score averages questions equally`() {
        // q1: 0.8 YES -> 0.08 ; q2: 0.3 NO -> 0.18 ; mean = 0.13
        val q1 = question(resolvedAtDays = 10.0, resolvedYes = true, forecasts = listOf(fc(0.0, 0.8)))
        val q2 = question(resolvedAtDays = 10.0, resolvedYes = false, forecasts = listOf(fc(0.0, 0.3)))
        assertThat(BrierScoring.overallBrierScore(listOf(q1, q2))).isWithin(1e-9).of(0.13)
    }

    @Test
    fun `a heavily-forecasted question does not dominate the overall score`() {
        // q1 has 10 (constant 0.8) forecasts but still contributes one score of 0.08.
        // A naive per-forecast pool would give ~0.089; per-question weighting gives 0.13.
        val manyForecasts = (0..9).map { fc(it.toDouble(), 0.8) }
        val q1 = question(resolvedAtDays = 10.0, resolvedYes = true, forecasts = manyForecasts)
        val q2 = question(resolvedAtDays = 10.0, resolvedYes = false, forecasts = listOf(fc(0.0, 0.3)))
        assertThat(BrierScoring.overallBrierScore(listOf(q1, q2))).isWithin(1e-9).of(0.13)
    }

    @Test
    fun `overall skips unscoreable questions and is null when none score`() {
        val noForecasts = question(resolvedAtDays = 10.0, resolvedYes = true, forecasts = emptyList())
        assertThat(BrierScoring.overallBrierScore(listOf(noForecasts))).isNull()
        assertThat(BrierScoring.overallBrierScore(emptyList())).isNull()

        // One scoreable + one unscoreable -> just the scoreable one's value.
        val scored = question(resolvedAtDays = 10.0, resolvedYes = true, forecasts = listOf(fc(0.0, 0.8)))
        assertThat(BrierScoring.overallBrierScore(listOf(scored, noForecasts)))
            .isWithin(1e-9).of(0.08)
    }
}
