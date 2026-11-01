package dev.russell.fatebook.ui.components

import com.google.common.truth.Truth.assertThat
import dev.russell.fatebook.domain.model.QuestionOption
import dev.russell.fatebook.domain.model.Resolution
import org.junit.Test

class OptionForecastMathTest {

    private fun option(id: String, forecast: Double? = null, resolution: Resolution? = null) =
        QuestionOption(id = id, text = id, latestForecast = forecast, resolution = resolution)

    // --- initialDrafts ---

    @Test
    fun `initialDrafts takes whole percents and leaves unforecast options unset`() {
        val drafts = OptionForecastMath.initialDrafts(listOf(option("a", 0.404), option("b")))

        assertThat(drafts).containsExactly("a", 40)
    }

    @Test
    fun `initialDrafts skips resolved options`() {
        val drafts = OptionForecastMath.initialDrafts(
            listOf(option("a", 0.4), option("b", 0.6, Resolution.NO)),
        )

        assertThat(drafts).containsExactly("a", 40)
    }

    // --- applyBits ---

    @Test
    fun `applyBits moves one bit of log-odds in whole percents`() {
        assertThat(OptionForecastMath.applyBits(50, 1)).isEqualTo(67)
        assertThat(OptionForecastMath.applyBits(50, -1)).isEqualTo(33)
    }

    @Test
    fun `applyBits stays within the ruler's range`() {
        assertThat(OptionForecastMath.applyBits(99, 1)).isEqualTo(99)
        assertThat(OptionForecastMath.applyBits(1, -1)).isEqualTo(1)
    }

    // --- changes ---

    @Test
    fun `changes lists only drafts that differ as a whole percent`() {
        val options = listOf(option("same", 0.404), option("moved", 0.3), option("new"), option("unset"))
        val drafts = mapOf("same" to 40, "moved" to 45, "new" to 10)

        val changes = OptionForecastMath.changes(options, drafts)

        assertThat(changes.map { it.first.id to it.second })
            .containsExactly("moved" to 45, "new" to 10)
            .inOrder()
    }

    // --- tally ---

    @Test
    fun `leftForOther is what the drafts leave of 100`() {
        assertThat(OptionForecastMath.leftForOther(mapOf("a" to 55, "b" to 25))).isEqualTo(20)
        assertThat(OptionForecastMath.leftForOther(emptyMap())).isEqualTo(100)
        assertThat(OptionForecastMath.leftForOther(mapOf("a" to 70, "b" to 40))).isEqualTo(0)
    }

    @Test
    fun `overBy is zero within 100`() {
        assertThat(OptionForecastMath.overBy(mapOf("a" to 60, "b" to 40))).isEqualTo(0)
        assertThat(OptionForecastMath.overBy(mapOf("a" to 70, "b" to 42))).isEqualTo(12)
    }

    // --- balance ---

    @Test
    fun `balance leaves drafts within 100 alone`() {
        val drafts = mapOf("a" to 50, "b" to 20)

        assertThat(OptionForecastMath.balance(drafts)).isEqualTo(drafts)
    }

    @Test
    fun `balance scales proportionally to exactly 100`() {
        assertThat(OptionForecastMath.balance(mapOf("a" to 60, "b" to 60)))
            .containsExactly("a", 50, "b", 50)
        assertThat(OptionForecastMath.balance(mapOf("a" to 80, "b" to 40)).values.sum())
            .isEqualTo(100)
    }

    @Test
    fun `balance hands leftover points to the largest remainders`() {
        // Exact shares: 62.5, 35.71, 1.79.
        assertThat(OptionForecastMath.balance(mapOf("a" to 70, "b" to 40, "c" to 2)))
            .containsExactly("a", 62, "b", 36, "c", 2)
    }

    @Test
    fun `balance keeps every option at the minimum and still totals 100`() {
        val drafts = mapOf("big" to 99, "b" to 1, "c" to 1, "d" to 1, "e" to 1)

        val balanced = OptionForecastMath.balance(drafts)

        assertThat(balanced.values.sum()).isEqualTo(100)
        balanced.values.forEach { assertThat(it).isAtLeast(1) }
    }
}
