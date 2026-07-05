package dev.russell.fatebook.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PieChartMathTest {

    // --- initialValues ---

    @Test
    fun `initialValues splits evenly when nothing is forecast`() {
        val values = PieChartMath.initialValues(listOf(null, null, null, null))

        assertThat(values).hasSize(4)
        values.forEach { assertThat(it).isWithin(0.001f).of(0.25f) }
    }

    @Test
    fun `initialValues keeps forecasts that already sum to 1`() {
        val values = PieChartMath.initialValues(listOf(0.5, 0.3, 0.2))

        assertThat(values[0]).isWithin(0.001f).of(0.5f)
        assertThat(values[1]).isWithin(0.001f).of(0.3f)
        assertThat(values[2]).isWithin(0.001f).of(0.2f)
    }

    @Test
    fun `initialValues gives unforecast options the leftover probability`() {
        val values = PieChartMath.initialValues(listOf(0.4, null, null))

        assertThat(values[0]).isWithin(0.001f).of(0.4f)
        assertThat(values[1]).isWithin(0.001f).of(0.3f)
        assertThat(values[2]).isWithin(0.001f).of(0.3f)
    }

    @Test
    fun `initialValues normalizes forecasts that do not sum to 1`() {
        // 0.35 + 0.35 = 0.7 with no room left for anyone else.
        val values = PieChartMath.initialValues(listOf(0.35, 0.35))

        assertThat(values.sum()).isWithin(0.001f).of(1f)
        assertThat(values[0]).isWithin(0.001f).of(0.5f)
        assertThat(values[1]).isWithin(0.001f).of(0.5f)
    }

    @Test
    fun `initialValues enforces the minimum slice`() {
        val values = PieChartMath.initialValues(listOf(1.0, 0.0, 0.0))

        assertThat(values.sum()).isWithin(0.001f).of(1f)
        values.forEach { assertThat(it).isAtLeast(PieChartMath.MIN_FRACTION - 0.0001f) }
    }

    @Test
    fun `initialValues handles empty input`() {
        assertThat(PieChartMath.initialValues(emptyList())).isEmpty()
    }

    // --- moveBoundary ---

    @Test
    fun `moveBoundary transfers probability between the two adjacent slices only`() {
        val values = listOf(0.25f, 0.25f, 0.25f, 0.25f)

        // Boundary 1 sits at 0.25; move it to 0.10.
        val moved = PieChartMath.moveBoundary(values, 1, 0.10f)

        assertThat(moved[0]).isWithin(0.001f).of(0.10f)
        assertThat(moved[1]).isWithin(0.001f).of(0.40f)
        assertThat(moved[2]).isWithin(0.001f).of(0.25f)
        assertThat(moved[3]).isWithin(0.001f).of(0.25f)
        assertThat(moved.sum()).isWithin(0.001f).of(1f)
    }

    @Test
    fun `moveBoundary clamps against the neighboring boundaries`() {
        val values = listOf(0.5f, 0.5f)

        // Dragging the only boundary all the way down can't shrink slice 0
        // below the minimum.
        val moved = PieChartMath.moveBoundary(values, 1, 0f)

        assertThat(moved[0]).isWithin(0.001f).of(PieChartMath.MIN_FRACTION)
        assertThat(moved[1]).isWithin(0.001f).of(1f - PieChartMath.MIN_FRACTION)
    }

    @Test
    fun `moveBoundary unwraps a drag across 12 o'clock`() {
        val values = listOf(0.05f, 0.95f)

        // Boundary sits at 0.05; a touch just past 12 o'clock reads as ~0.99.
        // That should clamp slice 0 to the minimum, not fling the boundary
        // to the far side.
        val moved = PieChartMath.moveBoundary(values, 1, 0.99f)

        assertThat(moved[0]).isWithin(0.001f).of(PieChartMath.MIN_FRACTION)
    }

    @Test
    fun `moveBoundary ignores invalid boundary indices`() {
        val values = listOf(0.5f, 0.5f)

        assertThat(PieChartMath.moveBoundary(values, 0, 0.3f)).isEqualTo(values)
        assertThat(PieChartMath.moveBoundary(values, 2, 0.3f)).isEqualTo(values)
    }

    // --- displayPercents ---

    @Test
    fun `displayPercents always sums to 100`() {
        val percents = PieChartMath.displayPercents(listOf(1f / 3f, 1f / 3f, 1f / 3f))

        assertThat(percents.sum()).isEqualTo(100)
        percents.forEach { assertThat(it).isAnyOf(33, 34) }
    }

    @Test
    fun `displayPercents matches exact values`() {
        assertThat(PieChartMath.displayPercents(listOf(0.5f, 0.3f, 0.2f)))
            .containsExactly(50, 30, 20)
            .inOrder()
    }

    // --- differsAsPercent ---

    @Test
    fun `differsAsPercent treats missing forecasts as changed`() {
        assertThat(PieChartMath.differsAsPercent(null, 0.4)).isTrue()
    }

    @Test
    fun `differsAsPercent compares at whole-percent precision`() {
        assertThat(PieChartMath.differsAsPercent(0.400, 0.404)).isFalse()
        assertThat(PieChartMath.differsAsPercent(0.40, 0.41)).isTrue()
    }
}
