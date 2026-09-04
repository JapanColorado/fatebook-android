package dev.russell.fatebook.ui.feed

import com.google.common.truth.Truth.assertThat
import dev.russell.fatebook.testutil.TestData
import org.junit.Test

class QuestionSearchTest {

    private val question = TestData.question(
        title = "Will it rain tomorrow?",
        tags = listOf("Weather", "daily"),
        options = listOf(
            TestData.questionOption(id = "o1", text = "Compose"),
            TestData.questionOption(id = "o2", text = "Flutter"),
        ),
    ).copy(notes = "Check the BBC forecast first")

    @Test
    fun `matches title case-insensitively`() {
        assertThat(QuestionSearch.matches(question, null, "RAIN".lowercase())).isTrue()
    }

    @Test
    fun `matches notes`() {
        assertThat(QuestionSearch.matches(question, null, "bbc")).isTrue()
    }

    @Test
    fun `matches tags`() {
        assertThat(QuestionSearch.matches(question, null, "weather")).isTrue()
    }

    @Test
    fun `matches option text`() {
        assertThat(QuestionSearch.matches(question, null, "flutter")).isTrue()
    }

    @Test
    fun `matches comment text when provided`() {
        assertThat(QuestionSearch.matches(question, "looks cloudy today", "cloudy")).isTrue()
        assertThat(QuestionSearch.matches(question, null, "cloudy")).isFalse()
    }

    @Test
    fun `no match returns false`() {
        assertThat(QuestionSearch.matches(question, "nothing here", "zebra")).isFalse()
    }
}
