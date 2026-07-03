package dev.russell.fatebook.data.sync

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import dev.russell.fatebook.data.local.PendingMutationEntity
import dev.russell.fatebook.data.local.Transactor
import dev.russell.fatebook.data.preferences.UserPreferences
import dev.russell.fatebook.data.repository.QuestionRepository
import dev.russell.fatebook.testutil.FakeCommentDao
import dev.russell.fatebook.testutil.FakeFatebookApi
import dev.russell.fatebook.testutil.FakeForecastDao
import dev.russell.fatebook.testutil.FakeOptionDao
import dev.russell.fatebook.testutil.FakePendingMutationDao
import dev.russell.fatebook.testutil.FakeQuestionDao
import dev.russell.fatebook.testutil.TestData
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.Instant
import java.time.LocalDate

class SyncRunnerTest {

    private lateinit var api: FakeFatebookApi
    private lateinit var questionDao: FakeQuestionDao
    private lateinit var forecastDao: FakeForecastDao
    private lateinit var commentDao: FakeCommentDao
    private lateinit var pendingDao: FakePendingMutationDao
    private lateinit var prefs: UserPreferences
    private lateinit var enqueuer: MutationEnqueuer
    private lateinit var repository: QuestionRepository
    private lateinit var runner: SyncRunner

    @Before
    fun setup() {
        api = FakeFatebookApi()
        questionDao = FakeQuestionDao()
        forecastDao = FakeForecastDao()
        commentDao = FakeCommentDao()
        pendingDao = FakePendingMutationDao()
        prefs = mockk(relaxed = true)
        every { prefs.apiKey } returns "test-api-key"
        enqueuer = MutationEnqueuer(pendingDao, Moshi.Builder().build())
        val transactor = Transactor { block -> block() }
        val noopScheduler = SyncScheduler { /* tests drive the runner directly */ }
        repository = QuestionRepository(
            api = api,
            dao = questionDao,
            forecastDao = forecastDao,
            commentDao = commentDao,
            optionDao = FakeOptionDao(),
            pendingDao = pendingDao,
            prefs = prefs,
            transactor = transactor,
            enqueuer = enqueuer,
            syncScheduler = noopScheduler,
            moshi = Moshi.Builder().build(),
        )
        runner = SyncRunner(
            api = api,
            dao = pendingDao,
            questionDao = questionDao,
            repository = repository,
            transactor = transactor,
            enqueuer = enqueuer,
            prefs = prefs,
        )
    }

    @Test
    fun `run returns SUCCESS when queue is empty`() = runTest {
        val outcome = runner.run()
        assertThat(outcome).isEqualTo(SyncRunner.Outcome.SUCCESS)
    }

    @Test
    fun `run returns SUCCESS when no api key is configured`() = runTest {
        every { prefs.apiKey } returns null
        enqueuer.enqueueAddForecast("q1", AddForecastPayload(0.7))

        val outcome = runner.run()

        assertThat(outcome).isEqualTo(SyncRunner.Outcome.SUCCESS)
        // Without an API key we don't drain — the row stays for later.
        assertThat(pendingDao.stored).hasSize(1)
        assertThat(api.addForecastCalls).isEmpty()
    }

    @Test
    fun `addForecast drains successfully and removes the row`() = runTest {
        enqueuer.enqueueAddForecast("q1", AddForecastPayload(0.7))

        runner.run()

        assertThat(api.addForecastCalls).hasSize(1)
        assertThat(api.addForecastCalls[0].first).isEqualTo("q1")
        assertThat(api.addForecastCalls[0].second).isEqualTo(0.7)
        assertThat(pendingDao.stored).isEmpty()
    }

    @Test
    fun `IOException returns RETRY and leaves the row PENDING`() = runTest {
        api.addForecastError = IOException("no network")
        enqueuer.enqueueAddForecast("q1", AddForecastPayload(0.7))

        val outcome = runner.run()

        assertThat(outcome).isEqualTo(SyncRunner.Outcome.RETRY)
        assertThat(pendingDao.stored).hasSize(1)
        assertThat(pendingDao.stored[0].status).isEqualTo(PendingMutationEntity.STATUS_PENDING)
    }

    @Test
    fun `5 consecutive HttpExceptions mark the row ERRORED`() = runTest {
        api.addForecastError = HttpException(Response.error<Any>(500, "".toResponseBody()))
        enqueuer.enqueueAddForecast("q1", AddForecastPayload(0.7))

        // 5 retries — each call returns RETRY and bumps attemptCount
        repeat(SyncRunner.MAX_HTTP_ATTEMPTS) { runner.run() }

        val row = pendingDao.stored.single()
        assertThat(row.status).isEqualTo(PendingMutationEntity.STATUS_ERRORED)
        assertThat(row.attemptCount).isEqualTo(SyncRunner.MAX_HTTP_ATTEMPTS)
        assertThat(row.lastError).contains("HTTP 500")
    }

    @Test
    fun `resetInFlight recovers a row left IN_FLIGHT by a crashed worker`() = runTest {
        pendingDao.insert(
            PendingMutationEntity(
                type = PendingMutationEntity.TYPE_ADD_FORECAST,
                questionLocalId = "q1",
                payloadJson = "{\"forecast\":0.7}",
                createdAtEpochMs = 0,
                status = PendingMutationEntity.STATUS_IN_FLIGHT,
            )
        )

        runner.run()

        // Row was reset to PENDING by run(), then drained
        assertThat(pendingDao.stored).isEmpty()
        assertThat(api.addForecastCalls).hasSize(1)
    }

    @Test
    fun `CREATE_QUESTION resolves temp id via URL parsing and rewrites follow-up mutations`() = runTest {
        // Simulate the user creating a question offline, then forecasting on it.
        val localId = repository.createQuestion("Test?", LocalDate.of(2030, 1, 1), 0.7)
        repository.addForecast(localId, 0.9)
        assertThat(pendingDao.stored).hasSize(2) // CREATE + ADD_FORECAST

        // Real Fatebook URLs end with `--<cuid>`. The runner parses the id from there.
        api.createQuestionResult = "https://fatebook.io/q/test-question--cm123abc456def"
        api.getQuestionsResponse = {
            TestData.questionsResponse(
                items = listOf(
                    TestData.questionDto(
                        id = "cm123abc456def",
                        title = "Test?",
                        resolveBy = "2030-01-01",
                    ),
                ),
            )
        }

        runner.run()

        // Queue drained entirely
        assertThat(pendingDao.stored).isEmpty()
        // Question was rewritten to the server id (FakeQuestionDao doesn't model
        // FK cascade, so we just check the row swapped ids)
        assertThat(questionDao.storedQuestions.map { it.id })
            .doesNotContain(localId)
        assertThat(questionDao.storedQuestions.map { it.id }).contains("cm123abc456def")
        // Follow-up forecast went to the *real* id, not the temp id
        assertThat(api.addForecastCalls).hasSize(1)
        assertThat(api.addForecastCalls[0].first).isEqualTo("cm123abc456def")
    }

    @Test
    fun `CREATE_QUESTION falls back to title+createdAt match when URL has no id suffix`() = runTest {
        repository.createQuestion("Test?", LocalDate.of(2030, 1, 1), 0.7)
        api.createQuestionResult = "https://fatebook.io/q/test-question"  // no --id suffix
        val nowIso = Instant.now().toString()
        api.getQuestionsResponse = {
            TestData.questionsResponse(
                items = listOf(
                    TestData.questionDto(
                        id = "server-fallback-id",
                        title = "Test?",
                        // Server returns a different resolveBy (timezone shift) — fallback ignores it.
                        resolveBy = "2030-01-02",
                        createdAt = nowIso,
                    ),
                ),
            )
        }

        runner.run()

        assertThat(pendingDao.stored).isEmpty()
        assertThat(questionDao.storedQuestions.map { it.id }).contains("server-fallback-id")
    }

    @Test
    fun `CREATE_QUESTION retry does not call api when server already has the question`() = runTest {
        val localId = repository.createQuestion("Already there?", LocalDate.of(2030, 1, 1), 0.5)
        // Pretend the server-side row is already present in cache from a previous attempt.
        questionDao.upsertAll(
            listOf(
                TestData.questionEntity(
                    id = "previously-created-server-id",
                    title = "Already there?",
                    createdAtEpochMs = System.currentTimeMillis(),
                )
            )
        )
        // The post-drain refresh would otherwise prune the cache; make the API
        // echo the same row back so it survives.
        api.getQuestionsResponse = {
            TestData.questionsResponse(
                items = listOf(
                    TestData.questionDto(
                        id = "previously-created-server-id",
                        title = "Already there?",
                    ),
                ),
            )
        }

        runner.run()

        // No duplicate api.createQuestion call — we skipped via the preExisting check.
        assertThat(api.createQuestionCalls).isEmpty()
        // Local row gone, server row remains, mutation gone.
        assertThat(questionDao.storedQuestions.map { it.id })
            .containsExactly("previously-created-server-id")
        assertThat(questionDao.storedQuestions.map { it.id }).doesNotContain(localId)
        assertThat(pendingDao.stored).isEmpty()
    }

    @Test
    fun `extractServerIdFromUrl parses canonical Fatebook URL`() {
        val id = SyncRunner.extractServerIdFromUrl("https://fatebook.io/q/will-it-rain--cm123abc456def")
        assertThat(id).isEqualTo("cm123abc456def")
    }

    @Test
    fun `extractServerIdFromUrl handles slugs containing double dashes`() {
        // The slug itself can contain `--`. We take the part after the LAST `--`.
        val id = SyncRunner.extractServerIdFromUrl("https://fatebook.io/q/why--really--cm123abc456def")
        assertThat(id).isEqualTo("cm123abc456def")
    }

    @Test
    fun `extractServerIdFromUrl returns null when URL has no double-dash`() {
        assertThat(SyncRunner.extractServerIdFromUrl("https://fatebook.io/q/no-id")).isNull()
    }

    @Test
    fun `extractServerIdFromUrl returns null when tail is not a clean id`() {
        // Trailing slash, query string, etc.
        assertThat(SyncRunner.extractServerIdFromUrl("https://fatebook.io/q/x--cm/path")).isNull()
        assertThat(SyncRunner.extractServerIdFromUrl("https://fatebook.io/q/x--has space")).isNull()
    }

    @Test
    fun `CREATE_QUESTION marks ERRORED if refresh finds no matching row`() = runTest {
        repository.createQuestion("Lonely?", LocalDate.of(2030, 1, 1), 0.5)

        // URL has no parseable id AND refresh returns nothing — no match possible.
        api.createQuestionResult = "https://fatebook.io/q/no-id"
        api.getQuestionsResponse = { TestData.questionsResponse(items = emptyList()) }

        runner.run()

        val row = pendingDao.stored.single()
        assertThat(row.status).isEqualTo(PendingMutationEntity.STATUS_ERRORED)
        assertThat(row.lastError).contains("no server row matched")
    }

    @Test
    fun `DELETE drains and removes the row`() = runTest {
        questionDao.upsertAll(listOf(TestData.questionEntity(id = "q1")))
        repository.deleteQuestion("q1")
        // Repository's deleteQuestion enqueued a DELETE
        assertThat(pendingDao.stored).hasSize(1)

        runner.run()

        assertThat(api.deleteQuestionCalls).containsExactly("q1")
        assertThat(pendingDao.stored).isEmpty()
    }

    @Test
    fun `RESOLVE drains with the right payload`() = runTest {
        questionDao.upsertAll(listOf(TestData.questionEntity(id = "q1")))
        repository.resolveQuestion("q1", dev.russell.fatebook.domain.model.Resolution.YES)

        runner.run()

        assertThat(api.resolveQuestionCalls).hasSize(1)
        assertThat(api.resolveQuestionCalls[0].second).isEqualTo("YES")
    }
}
