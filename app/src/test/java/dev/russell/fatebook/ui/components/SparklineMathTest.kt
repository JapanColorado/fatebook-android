package dev.russell.fatebook.ui.components

import com.google.common.truth.Truth.assertThat
import dev.russell.fatebook.domain.model.Forecast
import org.junit.Test
import java.time.Instant

class SparklineMathTest {

    private fun f(userId: String, value: Double, day: Int, optionId: String? = null) = Forecast(
        userId = userId,
        forecast = value,
        createdAt = Instant.parse("2030-01-0${day}T00:00:00Z"),
        optionId = optionId,
    )

    // --- yourForecasts ---

    @Test
    fun `picks the current user's binary forecasts, oldest first`() {
        val history = listOf(f("me", 0.7, 3), f("them", 0.2, 2), f("me", 0.4, 1), f("me", 0.9, 4, optionId = "o1"))

        val mine = SparklineMath.yourForecasts(history, currentUserId = "me")

        assertThat(mine.map { it.forecast }).containsExactly(0.4, 0.7).inOrder()
    }

    @Test
    fun `local forecasts with empty userId always count as yours`() {
        val history = listOf(f("me", 0.4, 1), f("", 0.6, 2))

        assertThat(SparklineMath.yourForecasts(history, "me")).hasSize(2)
        assertThat(SparklineMath.yourForecasts(history, "someone-else")).hasSize(1)
    }

    @Test
    fun `unknown user id shows everything on a single-forecaster question`() {
        val history = listOf(f("a", 0.4, 1), f("a", 0.6, 2))

        assertThat(SparklineMath.yourForecasts(history, null)).hasSize(2)
    }

    @Test
    fun `unknown user id hides a shared question's forecasts`() {
        val history = listOf(f("a", 0.4, 1), f("b", 0.6, 2))

        assertThat(SparklineMath.yourForecasts(history, null)).isEmpty()
    }

    // --- normalize ---

    @Test
    fun `normalize maps time and probability into unit space`() {
        val start = Instant.parse("2030-01-01T00:00:00Z")
        val end = Instant.parse("2030-01-05T00:00:00Z")
        val points = SparklineMath.normalize(
            listOf(
                Instant.parse("2030-01-03T00:00:00Z") to 0.25,
                Instant.parse("2030-01-01T00:00:00Z") to 1.5,
            ),
            start,
            end,
        )

        assertThat(points).containsExactly(SparklinePoint(0f, 1f), SparklinePoint(0.5f, 0.25f)).inOrder()
    }

    @Test
    fun `normalize with a degenerate span spaces points evenly`() {
        val at = Instant.parse("2030-01-01T00:00:00Z")
        val points = SparklineMath.normalize(listOf(at to 0.1, at to 0.2, at to 0.3), at, at)

        assertThat(points.map { it.x }).containsExactly(0f, 0.5f, 1f).inOrder()
    }

    @Test
    fun `normalize clamps times outside the span`() {
        val start = Instant.parse("2030-01-02T00:00:00Z")
        val end = Instant.parse("2030-01-03T00:00:00Z")
        val points = SparklineMath.normalize(listOf(Instant.parse("2030-01-01T00:00:00Z") to 0.5), start, end)

        assertThat(points.single().x).isEqualTo(0f)
    }

    // --- stepPolyline ---

    @Test
    fun `stepPolyline holds each value until the next and extends to the right edge`() {
        val line = SparklineMath.stepPolyline(listOf(SparklinePoint(0f, 0.2f), SparklinePoint(0.5f, 0.8f)))

        assertThat(line).containsExactly(
            SparklinePoint(0f, 0.2f),
            SparklinePoint(0.5f, 0.2f),
            SparklinePoint(0.5f, 0.8f),
            SparklinePoint(1f, 0.8f),
        ).inOrder()
    }

    @Test
    fun `stepPolyline of empty input is empty`() {
        assertThat(SparklineMath.stepPolyline(emptyList())).isEmpty()
    }
}
