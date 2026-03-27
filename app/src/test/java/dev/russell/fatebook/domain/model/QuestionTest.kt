package dev.russell.fatebook.domain.model

import com.google.common.truth.Truth.assertThat
import dev.russell.fatebook.testutil.TestData
import org.junit.Test
import java.time.Instant

class QuestionTest {

    @Test
    fun `isReadyToResolve true when unresolved and past resolveBy`() {
        val question = TestData.question(
            resolved = false,
            resolveBy = Instant.parse("2020-01-01T00:00:00Z"), // far past
        )
        assertThat(question.isReadyToResolve).isTrue()
    }

    @Test
    fun `isReadyToResolve false when resolved`() {
        val question = TestData.question(
            resolved = true,
            resolveBy = Instant.parse("2020-01-01T00:00:00Z"),
        )
        assertThat(question.isReadyToResolve).isFalse()
    }

    @Test
    fun `isReadyToResolve false when resolveBy is in the future`() {
        val question = TestData.question(
            resolved = false,
            resolveBy = Instant.parse("2099-01-01T00:00:00Z"),
        )
        assertThat(question.isReadyToResolve).isFalse()
    }

    @Test
    fun `forecastPercent rounds correctly`() {
        val question = TestData.question(yourLatestForecast = 0.753)
        assertThat(question.forecastPercent).isEqualTo(75)
    }

    @Test
    fun `forecastPercent null when no forecast`() {
        val question = TestData.question(yourLatestForecast = null)
        assertThat(question.forecastPercent).isNull()
    }

    @Test
    fun `isForecastHidden true when hiddenUntil is in the future`() {
        val question = TestData.question(
            forecastHiddenUntil = Instant.parse("2099-01-01T00:00:00Z"),
        )
        assertThat(question.isForecastHidden).isTrue()
    }

    @Test
    fun `isForecastHidden false when hiddenUntil is in the past`() {
        val question = TestData.question(
            forecastHiddenUntil = Instant.parse("2020-01-01T00:00:00Z"),
        )
        assertThat(question.isForecastHidden).isFalse()
    }

    @Test
    fun `isForecastHidden false when hiddenUntil is null`() {
        val question = TestData.question(forecastHiddenUntil = null)
        assertThat(question.isForecastHidden).isFalse()
    }
}
