package dev.russell.fatebook.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.russell.fatebook.data.preferences.UserPreferences
import dev.russell.fatebook.data.remote.dto.ForecastDto
import dev.russell.fatebook.data.remote.dto.QuestionsResponseDto
import dev.russell.fatebook.domain.model.Resolution
import dev.russell.fatebook.testutil.FakeFatebookApi
import dev.russell.fatebook.testutil.FakeQuestionDao
import dev.russell.fatebook.testutil.TestData
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

class QuestionRepositoryTest {

    private lateinit var api: FakeFatebookApi
    private lateinit var dao: FakeQuestionDao
    private lateinit var prefs: UserPreferences
    private lateinit var repository: QuestionRepository

    @Before
    fun setup() {
        api = FakeFatebookApi()
        dao = FakeQuestionDao()
        prefs = mockk(relaxed = true)
        every { prefs.apiKey } returns "test-api-key"
        repository = QuestionRepository(api, dao, prefs)
    }

    // --- refresh ---

    @Test
    fun `refresh clears and replaces local cache`() = runTest {
        val dto1 = TestData.questionDto(id = "q1")
        val dto2 = TestData.questionDto(id = "q2")
        api.getQuestionsResponse = { TestData.questionsResponse(items = listOf(dto1, dto2)) }

        repository.refresh()

        assertThat(dao.deleteAllCallCount).isEqualTo(1)
        assertThat(dao.storedQuestions).hasSize(2)
        assertThat(dao.storedQuestions.map { it.id }).containsExactly("q1", "q2")
    }

    @Test
    fun `refresh sets nextCursor from response`() = runTest {
        api.getQuestionsResponse = { TestData.questionsResponse(nextCursor = 42) }

        repository.refresh()

        assertThat(repository.hasMore()).isTrue()
    }

    @Test
    fun `refresh propagates API exception`() = runTest {
        api.getQuestionsResponse = { throw RuntimeException("Network error") }

        val result = runCatching { repository.refresh() }

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()!!.message).isEqualTo("Network error")
    }

    @Test
    fun `refresh returns domain questions`() = runTest {
        val dto = TestData.questionDto(id = "q1", title = "Test?")
        api.getQuestionsResponse = { TestData.questionsResponse(items = listOf(dto)) }

        val result = repository.refresh()

        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo("q1")
        assertThat(result[0].title).isEqualTo("Test?")
    }

    // --- loadMore ---

    @Test
    fun `loadMore appends to cache without deleting`() = runTest {
        // First page
        api.getQuestionsResponse = { cursor ->
            if (cursor == null) TestData.questionsResponse(
                items = listOf(TestData.questionDto(id = "q1")),
                nextCursor = 2,
            )
            else TestData.questionsResponse(
                items = listOf(TestData.questionDto(id = "q2")),
            )
        }
        repository.refresh()
        val deleteCountAfterRefresh = dao.deleteAllCallCount

        repository.loadMore()

        assertThat(dao.deleteAllCallCount).isEqualTo(deleteCountAfterRefresh)
        assertThat(dao.storedQuestions.map { it.id }).containsExactly("q1", "q2")
    }

    @Test
    fun `loadMore returns false when no cursor`() = runTest {
        api.getQuestionsResponse = { TestData.questionsResponse(nextCursor = null) }
        repository.refresh()

        val result = repository.loadMore()

        assertThat(result).isFalse()
        assertThat(api.getQuestionsCalls).hasSize(1) // only refresh call, no loadMore call
    }

    @Test
    fun `loadMore updates nextCursor`() = runTest {
        api.getQuestionsResponse = { cursor ->
            when (cursor) {
                null -> TestData.questionsResponse(nextCursor = 2)
                2 -> TestData.questionsResponse(nextCursor = 3)
                else -> TestData.questionsResponse()
            }
        }
        repository.refresh()

        repository.loadMore() // cursor 2 -> 3
        assertThat(repository.hasMore()).isTrue()

        repository.loadMore() // cursor 3 -> null
        assertThat(repository.hasMore()).isFalse()
    }

    // --- createQuestion ---

    @Test
    fun `createQuestion sets lastPredictionDate`() = runTest {
        repository.createQuestion("Test?", java.time.LocalDate.of(2030, 1, 1), 0.7)

        coVerify { prefs.setLastPredictionDate(any()) }
    }

    @Test
    fun `createQuestion refreshes cache after creation`() = runTest {
        repository.createQuestion("Test?", java.time.LocalDate.of(2030, 1, 1), 0.7)

        // createQuestion calls refresh internally, so getQuestions was called
        assertThat(api.createQuestionCalls).hasSize(1)
        assertThat(api.getQuestionsCalls).isNotEmpty()
    }

    // --- addForecast ---

    @Test
    fun `addForecast throws when no API key`() = runTest {
        every { prefs.apiKey } returns null

        val result = runCatching { repository.addForecast("q1", 0.8) }

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `addForecast calls API with correct params and refreshes`() = runTest {
        repository.addForecast("q1", 0.8)

        assertThat(api.addForecastCalls).hasSize(1)
        val (qId, forecast, key) = api.addForecastCalls[0]
        assertThat(qId).isEqualTo("q1")
        assertThat(forecast).isEqualTo(0.8)
        assertThat(key).isEqualTo("test-api-key")
        // Verify refresh was called after addForecast
        assertThat(api.getQuestionsCalls).isNotEmpty()
    }

    // --- resolveQuestion ---

    @Test
    fun `resolveQuestion calls API with correct resolution value`() = runTest {
        repository.resolveQuestion("q1", Resolution.YES)

        assertThat(api.resolveQuestionCalls).hasSize(1)
        val (qId, resolution, key) = api.resolveQuestionCalls[0]
        assertThat(qId).isEqualTo("q1")
        assertThat(resolution).isEqualTo("YES")
        assertThat(key).isEqualTo("test-api-key")
    }

    @Test
    fun `resolveQuestion throws when no API key`() = runTest {
        every { prefs.apiKey } returns null

        val result = runCatching { repository.resolveQuestion("q1", Resolution.NO) }

        assertThat(result.isFailure).isTrue()
    }

    // --- validateApiKey ---

    @Test
    fun `validateApiKey returns true on success`() = runTest {
        val result = repository.validateApiKey()
        assertThat(result).isTrue()
    }

    @Test
    fun `validateApiKey returns false on exception`() = runTest {
        api.validateApiKeyError = RuntimeException("Unauthorized")

        val result = repository.validateApiKey()

        assertThat(result).isFalse()
    }

    // --- observe flows ---

    @Test
    fun `observeActive maps entities to domain models`() = runTest {
        val entity = TestData.questionEntity(
            id = "q1",
            title = "Test?",
            resolved = false,
            resolution = null,
            latestForecast = 0.75,
        )
        dao.upsertAll(listOf(entity))

        repository.observeActive().test {
            val questions = awaitItem()
            assertThat(questions).hasSize(1)
            assertThat(questions[0].id).isEqualTo("q1")
            assertThat(questions[0].title).isEqualTo("Test?")
            assertThat(questions[0].yourLatestForecast).isEqualTo(0.75)
            assertThat(questions[0].resolved).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- mapper edge cases ---

    @Test
    fun `toEntity extracts latest forecast by createdAt`() = runTest {
        val dto = TestData.questionDto(
            forecasts = listOf(
                ForecastDto("u1", 0.3, "2020-01-01T00:00:00Z", null),
                ForecastDto("u1", 0.9, "2020-06-01T00:00:00Z", null),
                ForecastDto("u1", 0.5, "2020-03-01T00:00:00Z", null),
            )
        )
        api.getQuestionsResponse = { QuestionsResponseDto(listOf(dto), null) }

        repository.refresh()

        assertThat(dao.storedQuestions[0].latestForecast).isEqualTo(0.9)
    }

    @Test
    fun `toEntity handles null forecasts`() = runTest {
        val dto = TestData.questionDto(forecasts = null)
        api.getQuestionsResponse = { QuestionsResponseDto(listOf(dto), null) }

        repository.refresh()

        assertThat(dao.storedQuestions[0].latestForecast).isNull()
    }

    @Test
    fun `toEntity handles empty forecasts`() = runTest {
        val dto = TestData.questionDto(forecasts = emptyList())
        api.getQuestionsResponse = { QuestionsResponseDto(listOf(dto), null) }

        repository.refresh()

        assertThat(dao.storedQuestions[0].latestForecast).isNull()
    }

    @Test
    fun `parseInstant handles ISO 8601 format`() = runTest {
        val dto = TestData.questionDto(resolveBy = "2030-06-01T12:30:00Z")
        api.getQuestionsResponse = { QuestionsResponseDto(listOf(dto), null) }

        repository.refresh()

        assertThat(dao.storedQuestions[0].resolveByEpochMs)
            .isEqualTo(Instant.parse("2030-06-01T12:30:00Z").toEpochMilli())
    }

    @Test
    fun `parseInstant handles date-only format`() = runTest {
        val dto = TestData.questionDto(resolveBy = "2030-06-01")
        api.getQuestionsResponse = { QuestionsResponseDto(listOf(dto), null) }

        repository.refresh()

        assertThat(dao.storedQuestions[0].resolveByEpochMs)
            .isEqualTo(Instant.parse("2030-06-01T00:00:00Z").toEpochMilli())
    }

    @Test
    fun `toEntity maps hideForecastsUntil`() = runTest {
        val dto = TestData.questionDto(
            forecasts = listOf(
                ForecastDto("u1", 0.7, "2020-01-02T00:00:00Z", "2030-01-01T00:00:00Z"),
            )
        )
        api.getQuestionsResponse = { QuestionsResponseDto(listOf(dto), null) }

        repository.refresh()

        assertThat(dao.storedQuestions[0].forecastHiddenUntilEpochMs)
            .isEqualTo(Instant.parse("2030-01-01T00:00:00Z").toEpochMilli())
    }
}
