package dev.russell.fatebook.domain.model

import com.google.common.truth.Truth.assertThat
import dev.russell.fatebook.testutil.TestData
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

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
    fun `isReadyToResolve true when resolveByDate equals today`() {
        // Build an Instant whose UTC date matches today's local date,
        // so resolveByDate (UTC-extracted) == LocalDate.now() (system tz).
        val todayInstant = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant()
        val question = TestData.question(
            resolved = false,
            resolveBy = todayInstant,
        )
        assertThat(question.isReadyToResolve).isTrue()
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

    // --- resolvesLabel ---

    @Test
    fun `resolvesLabel is Resolved when resolved`() {
        val question = TestData.question(
            resolved = true,
            resolveBy = Instant.parse("2099-01-01T00:00:00Z"), // future, but already resolved
        )
        assertThat(question.resolvesLabel).isEqualTo("Resolved")
    }

    @Test
    fun `resolvesLabel is Resolves when overdue but not yet resolved`() {
        val question = TestData.question(
            resolved = false,
            resolveBy = Instant.parse("2020-01-01T00:00:00Z"), // far past, still unresolved
        )
        assertThat(question.resolvesLabel).isEqualTo("Resolves")
    }

    @Test
    fun `resolvesLabel is Resolves when unresolved and resolveBy in the future`() {
        val question = TestData.question(
            resolved = false,
            resolveBy = Instant.parse("2099-01-01T00:00:00Z"), // far future
        )
        assertThat(question.resolvesLabel).isEqualTo("Resolves")
    }

    // --- isForecastHidden ---

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
