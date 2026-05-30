package dev.russell.fatebook.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import dev.russell.fatebook.data.local.PendingMutationEntity
import dev.russell.fatebook.data.local.Transactor
import dev.russell.fatebook.data.preferences.UserPreferences
import dev.russell.fatebook.data.remote.dto.ForecastDto
import dev.russell.fatebook.data.remote.dto.QuestionsResponseDto
import dev.russell.fatebook.data.sync.MutationEnqueuer
import dev.russell.fatebook.data.sync.SyncScheduler
import dev.russell.fatebook.domain.model.Resolution
import dev.russell.fatebook.testutil.FakeCommentDao
import dev.russell.fatebook.testutil.FakeFatebookApi
import dev.russell.fatebook.testutil.FakeForecastDao
import dev.russell.fatebook.testutil.FakePendingMutationDao
import dev.russell.fatebook.testutil.FakeQuestionDao
import dev.russell.fatebook.testutil.TestData
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class QuestionRepositoryTest {

    private lateinit var api: FakeFatebookApi
    private lateinit var dao: FakeQuestionDao
    private lateinit var forecastDao: FakeForecastDao
    private lateinit var commentDao: FakeCommentDao
    private lateinit var pendingDao: FakePendingMutationDao
    private lateinit var prefs: UserPreferences
    private lateinit var enqueuer: MutationEnqueuer
    private var scheduleCallCount = 0
    private lateinit var repository: QuestionRepository

    @Before
    fun setup() {
        api = FakeFatebookApi()
        dao = FakeQuestionDao()
        forecastDao = FakeForecastDao()
        commentDao = FakeCommentDao()
        pendingDao = FakePendingMutationDao()
        prefs = mockk(relaxed = true)
        every { prefs.apiKey } returns "test-api-key"
        enqueuer = MutationEnqueuer(pendingDao, Moshi.Builder().build())
        scheduleCallCount = 0
        val syncScheduler = SyncScheduler { scheduleCallCount++ }
        val transactor = Transactor { block -> block() }
        repository = QuestionRepository(
            api = api,
            dao = dao,
            forecastDao = forecastDao,
            commentDao = commentDao,
            pendingDao = pendingDao,
            prefs = prefs,
            transactor = transactor,
            enqueuer = enqueuer,
            syncScheduler = syncScheduler,
        )
    }

    // --- refresh ---

    @Test
    fun `refresh upserts response items into local cache`() = runTest {
        val dto1 = TestData.questionDto(id = "q1")
        val dto2 = TestData.questionDto(id = "q2")
        api.getQuestionsResponse = { TestData.questionsResponse(items = listOf(dto1, dto2)) }

        repository.refresh()

        assertThat(dao.storedQuestions).hasSize(2)
        assertThat(dao.storedQuestions.map { it.id }).containsExactly("q1", "q2")
        assertThat(dao.deleteAllCallCount).isEqualTo(0)
    }

    @Test
    fun `refresh prunes questions absent from response`() = runTest {
        dao.upsertAll(listOf(TestData.questionEntity(id = "stale"), TestData.questionEntity(id = "keep")))
        api.getQuestionsResponse = {
            TestData.questionsResponse(items = listOf(TestData.questionDto(id = "keep")))
        }

        repository.refresh()

        assertThat(dao.storedQuestions.map { it.id }).containsExactly("keep")
    }

    @Test
    fun `refresh preserves locally-created questions`() = runTest {
        // A locally-created question that hasn't synced yet
        dao.upsertAll(listOf(TestData.questionEntity(id = "local-abc")))
        api.getQuestionsResponse = {
            TestData.questionsResponse(items = listOf(TestData.questionDto(id = "server-1")))
        }

        repository.refresh()

        // Both rows survive: server one is inserted, local one is NOT pruned
        assertThat(dao.storedQuestions.map { it.id })
            .containsExactly("local-abc", "server-1")
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

    @Test
    fun `refresh maps resolvedAt into the cache`() = runTest {
        val dto = TestData.questionDto(
            id = "q1",
            resolved = true,
            resolution = "YES",
            resolvedAt = "2024-03-15T12:00:00Z",
        )
        api.getQuestionsResponse = { TestData.questionsResponse(items = listOf(dto)) }

        repository.refresh()

        val expected = java.time.Instant.parse("2024-03-15T12:00:00Z").toEpochMilli()
        assertThat(dao.storedQuestions.single().resolvedAtEpochMs).isEqualTo(expected)
    }

    // --- loadMore ---

    @Test
    fun `loadMore appends to cache without pruning`() = runTest {
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
        val pruneCountAfterRefresh = dao.deleteByIdsNotInCallCount

        repository.loadMore()

        assertThat(dao.deleteByIdsNotInCallCount).isEqualTo(pruneCountAfterRefresh)
        assertThat(dao.storedQuestions.map { it.id }).containsExactly("q1", "q2")
    }

    @Test
    fun `loadMore returns false when no cursor`() = runTest {
        api.getQuestionsResponse = { TestData.questionsResponse(nextCursor = null) }
        repository.refresh()

        val result = repository.loadMore()

        assertThat(result).isFalse()
        assertThat(api.getQuestionsCalls).hasSize(1)
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

        repository.loadMore()
        assertThat(repository.hasMore()).isTrue()

        repository.loadMore()
        assertThat(repository.hasMore()).isFalse()
    }

    // --- questionType filtering ---

    @Test
    fun `refresh filters out multi-option questions`() = runTest {
        val binary = TestData.questionDto(id = "q1", questionType = "BINARY")
        val multi = TestData.questionDto(id = "q2", questionType = "MULTI_OPTION")
        val binary2 = TestData.questionDto(id = "q3", questionType = "BINARY")
        api.getQuestionsResponse = { TestData.questionsResponse(items = listOf(binary, multi, binary2)) }

        val result = repository.refresh()

        assertThat(dao.storedQuestions.map { it.id }).containsExactly("q1", "q3")
        assertThat(result.map { it.id }).containsExactly("q1", "q3")
    }

    @Test
    fun `refresh treats null questionType as binary`() = runTest {
        val nullType = TestData.questionDto(id = "q1", questionType = null)
        api.getQuestionsResponse = { TestData.questionsResponse(items = listOf(nullType)) }

        val result = repository.refresh()

        assertThat(dao.storedQuestions).hasSize(1)
        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo("q1")
    }

    // --- createQuestion (optimistic) ---

    @Test
    fun `createQuestion inserts a local-id row without hitting the API`() = runTest {
        val returnedId = repository.createQuestion("Test?", LocalDate.of(2030, 1, 1), 0.7)

        assertThat(returnedId).startsWith(PendingMutationEntity.LOCAL_ID_PREFIX)
        assertThat(dao.storedQuestions.map { it.id }).containsExactly(returnedId)
        assertThat(dao.storedQuestions[0].title).isEqualTo("Test?")
        assertThat(dao.storedQuestions[0].latestForecast).isEqualTo(0.7)
        assertThat(api.createQuestionCalls).isEmpty()
        assertThat(api.getQuestionsCalls).isEmpty()
    }

    @Test
    fun `createQuestion enqueues a CREATE_QUESTION mutation and schedules sync`() = runTest {
        repository.createQuestion("Test?", LocalDate.of(2030, 1, 1), 0.7)

        assertThat(pendingDao.stored).hasSize(1)
        assertThat(pendingDao.stored[0].type).isEqualTo(PendingMutationEntity.TYPE_CREATE_QUESTION)
        assertThat(scheduleCallCount).isEqualTo(1)
    }

    @Test
    fun `createQuestion seeds an initial forecast row`() = runTest {
        repository.createQuestion("Test?", LocalDate.of(2030, 1, 1), 0.7)

        assertThat(forecastDao.storedForecasts).hasSize(1)
        assertThat(forecastDao.storedForecasts[0].forecast).isEqualTo(0.7)
    }

    @Test
    fun `createQuestion sets lastPredictionDate`() = runTest {
        repository.createQuestion("Test?", LocalDate.of(2030, 1, 1), 0.7)

        coVerify { prefs.setLastPredictionDate(any()) }
    }

    // --- addForecast (optimistic) ---

    @Test
    fun `addForecast applies local update and enqueues without an API key`() = runTest {
        dao.upsertAll(listOf(TestData.questionEntity(id = "q1", latestForecast = 0.5)))
        every { prefs.apiKey } returns null

        repository.addForecast("q1", 0.8)

        assertThat(dao.storedQuestions.first { it.id == "q1" }.latestForecast).isEqualTo(0.8)
        assertThat(pendingDao.stored.single().type).isEqualTo(PendingMutationEntity.TYPE_ADD_FORECAST)
        assertThat(api.addForecastCalls).isEmpty()
    }

    @Test
    fun `addForecast triggers sync and inserts a forecast row`() = runTest {
        dao.upsertAll(listOf(TestData.questionEntity(id = "q1")))

        repository.addForecast("q1", 0.8)

        assertThat(scheduleCallCount).isEqualTo(1)
        assertThat(forecastDao.storedForecasts.map { it.forecast }).contains(0.8)
    }

    @Test
    fun `addForecast sets lastPredictionDate`() = runTest {
        repository.addForecast("q1", 0.8)

        coVerify { prefs.setLastPredictionDate(any()) }
    }

    // --- resolveQuestion (optimistic) ---

    @Test
    fun `resolveQuestion applies local update and enqueues`() = runTest {
        dao.upsertAll(listOf(TestData.questionEntity(id = "q1", resolved = false)))

        repository.resolveQuestion("q1", Resolution.YES)

        assertThat(dao.storedQuestions[0].resolved).isTrue()
        assertThat(dao.storedQuestions[0].resolution).isEqualTo("YES")
        assertThat(dao.storedQuestions[0].resolvedAtEpochMs).isNotNull()
        assertThat(pendingDao.stored.single().type).isEqualTo(PendingMutationEntity.TYPE_RESOLVE)
        assertThat(api.resolveQuestionCalls).isEmpty()
        assertThat(scheduleCallCount).isEqualTo(1)
    }

    // --- editQuestion (optimistic) ---

    @Test
    fun `editQuestion applies local update and enqueues`() = runTest {
        dao.upsertAll(listOf(TestData.questionEntity(id = "q1", title = "Old")))

        repository.editQuestion("q1", title = "New")

        assertThat(dao.storedQuestions[0].title).isEqualTo("New")
        assertThat(pendingDao.stored.single().type).isEqualTo(PendingMutationEntity.TYPE_EDIT)
        assertThat(api.editQuestionCalls).isEmpty()
    }

    // --- deleteQuestion (optimistic) ---

    @Test
    fun `deleteQuestion of a server-id row removes it locally and enqueues DELETE`() = runTest {
        dao.upsertAll(listOf(TestData.questionEntity(id = "q1")))

        repository.deleteQuestion("q1")

        assertThat(dao.storedQuestions).isEmpty()
        assertThat(pendingDao.stored.single().type).isEqualTo(PendingMutationEntity.TYPE_DELETE)
        assertThat(api.deleteQuestionCalls).isEmpty()
    }

    @Test
    fun `deleteQuestion of a local-id row collapses the queued CREATE`() = runTest {
        // Simulate offline create
        val localId = repository.createQuestion("Test?", LocalDate.of(2030, 1, 1), 0.7)
        assertThat(pendingDao.stored).hasSize(1) // CREATE is queued

        // Now delete it before it syncs — should collapse to a noop
        repository.deleteQuestion(localId)

        assertThat(dao.storedQuestions).isEmpty()
        assertThat(pendingDao.stored).isEmpty()
    }

    // --- setSharedPublicly (optimistic) ---

    @Test
    fun `setSharedPublicly applies local update and enqueues`() = runTest {
        dao.upsertAll(listOf(TestData.questionEntity(id = "q1").copy(sharedPublicly = false)))

        repository.setSharedPublicly("q1", sharedPublicly = true, unlisted = false)

        assertThat(dao.storedQuestions[0].sharedPublicly).isTrue()
        assertThat(pendingDao.stored.single().type).isEqualTo(PendingMutationEntity.TYPE_SET_SHARED)
    }

    // --- addComment (optimistic) ---

    @Test
    fun `addComment inserts local comment and enqueues without calling API`() = runTest {
        val result = repository.addComment("q1", "Nice!")

        assertThat(result.comment).isEqualTo("Nice!")
        assertThat(commentDao.storedComments.single().comment).isEqualTo("Nice!")
        assertThat(pendingDao.stored.single().type).isEqualTo(PendingMutationEntity.TYPE_ADD_COMMENT)
        assertThat(api.addCommentCalls).isEmpty()
    }

    // --- sync-issue helpers ---

    @Test
    fun `discardErroredMutation of CREATE also deletes local duplicate if server has it`() = runTest {
        // Simulate the bug state: local-id row + server-id row both present,
        // CREATE mutation in ERRORED status.
        val localId = repository.createQuestion("Dup?", LocalDate.of(2030, 1, 1), 0.5)
        // Pretend a refresh has put a server copy in the cache.
        dao.upsertAll(
            listOf(
                TestData.questionEntity(
                    id = "server-id-real",
                    title = "Dup?",
                    createdAtEpochMs = System.currentTimeMillis(),
                )
            )
        )
        // Mark the CREATE mutation as errored to mimic a failed reconciliation.
        val mutationId = pendingDao.stored.single().id
        pendingDao.markErrored(mutationId, "no matching server row appeared")

        repository.discardErroredMutation(mutationId)

        // Mutation gone, AND local duplicate cleaned up, server row stays.
        assertThat(pendingDao.stored).isEmpty()
        assertThat(dao.storedQuestions.map { it.id }).containsExactly("server-id-real")
        assertThat(dao.storedQuestions.map { it.id }).doesNotContain(localId)
    }

    @Test
    fun `discardErroredMutation of CREATE keeps local row when server has no copy`() = runTest {
        val localId = repository.createQuestion("Solo?", LocalDate.of(2030, 1, 1), 0.5)
        val mutationId = pendingDao.stored.single().id
        pendingDao.markErrored(mutationId, "HTTP 500")

        repository.discardErroredMutation(mutationId)

        // Mutation gone, but local row stays (no server copy to assume).
        assertThat(pendingDao.stored).isEmpty()
        assertThat(dao.storedQuestions.map { it.id }).containsExactly(localId)
    }

    @Test
    fun `retryAllErroredMutations clears errored rows and schedules sync`() = runTest {
        // Manually insert an errored mutation
        pendingDao.insert(
            PendingMutationEntity(
                type = PendingMutationEntity.TYPE_ADD_FORECAST,
                questionLocalId = "q1",
                payloadJson = "{\"forecast\":0.7}",
                createdAtEpochMs = 0,
                status = PendingMutationEntity.STATUS_ERRORED,
                attemptCount = 5,
                lastError = "HTTP 500",
            )
        )

        repository.retryAllErroredMutations()

        assertThat(pendingDao.stored.single().status).isEqualTo(PendingMutationEntity.STATUS_PENDING)
        assertThat(scheduleCallCount).isEqualTo(1)
    }

    // --- validateApiKey ---

    @Test
    fun `validateApiKey returns true on success`() = runTest {
        assertThat(repository.validateApiKey()).isTrue()
    }

    @Test
    fun `validateApiKey returns false on exception`() = runTest {
        api.validateApiKeyError = RuntimeException("Unauthorized")

        assertThat(repository.validateApiKey()).isFalse()
    }

    // --- forecast storage during refresh ---

    @Test
    fun `refresh stores forecasts alongside questions`() = runTest {
        val dto = TestData.questionDto(
            id = "q1",
            forecasts = listOf(
                ForecastDto("u1", 0.3, "2020-01-01T00:00:00Z", null),
                ForecastDto("u1", 0.7, "2020-02-01T00:00:00Z", null),
            ),
        )
        api.getQuestionsResponse = { QuestionsResponseDto(listOf(dto), null) }

        repository.refresh()

        assertThat(forecastDao.storedForecasts).hasSize(2)
        assertThat(forecastDao.storedForecasts.map { it.forecast }).containsExactly(0.3, 0.7)
    }

    // --- loadAllQuestions ---

    @Test
    fun `loadAllQuestions fetches all pages`() = runTest {
        api.getQuestionsResponse = { cursor ->
            when (cursor) {
                null -> TestData.questionsResponse(
                    items = listOf(TestData.questionDto(id = "q1")),
                    nextCursor = 2,
                )
                2 -> TestData.questionsResponse(
                    items = listOf(TestData.questionDto(id = "q2")),
                    nextCursor = 3,
                )
                3 -> TestData.questionsResponse(
                    items = listOf(TestData.questionDto(id = "q3")),
                )
                else -> TestData.questionsResponse()
            }
        }

        repository.loadAllQuestions()

        assertThat(dao.storedQuestions.map { it.id }).containsExactly("q1", "q2", "q3")
        assertThat(repository.hasMore()).isFalse()
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

    // --- countReadyToResolve ---

    @Test
    fun `countReadyToResolve returns count of unresolved past-due questions`() = runTest {
        val pastDue = TestData.questionEntity(
            id = "q1",
            resolved = false,
            resolveByEpochMs = Instant.parse("2020-01-01T00:00:00Z").toEpochMilli(),
        )
        val futureDue = TestData.questionEntity(
            id = "q2",
            resolved = false,
            resolveByEpochMs = Instant.parse("2099-01-01T00:00:00Z").toEpochMilli(),
        )
        val resolvedPastDue = TestData.questionEntity(
            id = "q3",
            resolved = true,
            resolveByEpochMs = Instant.parse("2020-01-01T00:00:00Z").toEpochMilli(),
        )
        dao.upsertAll(listOf(pastDue, futureDue, resolvedPastDue))

        val count = repository.countReadyToResolve()

        assertThat(count).isEqualTo(1)
    }
}
