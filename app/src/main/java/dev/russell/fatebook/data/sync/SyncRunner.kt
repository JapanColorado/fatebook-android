package dev.russell.fatebook.data.sync

import dev.russell.fatebook.data.local.PendingMutationDao
import dev.russell.fatebook.data.local.PendingMutationEntity
import dev.russell.fatebook.data.local.QuestionDao
import dev.russell.fatebook.data.local.Transactor
import dev.russell.fatebook.data.preferences.UserPreferences
import dev.russell.fatebook.data.remote.FatebookApi
import dev.russell.fatebook.data.repository.QuestionRepository
import retrofit2.HttpException
import java.io.IOException
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure-Kotlin core of [SyncWorker] — extracted so it's testable on JVM without
 * a WorkManager runtime. Worker's [doWork] just delegates here.
 */
@Singleton
class SyncRunner @Inject constructor(
    private val api: FatebookApi,
    private val dao: PendingMutationDao,
    private val questionDao: QuestionDao,
    private val repository: QuestionRepository,
    private val transactor: Transactor,
    private val enqueuer: MutationEnqueuer,
    private val prefs: UserPreferences,
) {
    enum class Outcome { SUCCESS, RETRY }

    /** Map populated when a CREATE_QUESTION resolves to its server id. */
    private val tempIdToRealId = mutableMapOf<String, String>()

    suspend fun run(): Outcome {
        val apiKey = prefs.apiKey ?: return Outcome.SUCCESS
        dao.resetInFlight()
        tempIdToRealId.clear()
        var drainedAny = false

        while (true) {
            val next = dao.nextPending() ?: break
            dao.markInFlight(next.id)

            val questionId = remap(next.questionLocalId)
            try {
                when (next.type) {
                    PendingMutationEntity.TYPE_CREATE_QUESTION ->
                        syncCreate(next)
                    PendingMutationEntity.TYPE_ADD_FORECAST -> {
                        val payload = enqueuer.decodeForecast(next.payloadJson)
                        api.addForecast(questionId, payload.forecast, apiKey, payload.optionId)
                        dao.delete(next.id)
                    }
                    PendingMutationEntity.TYPE_RESOLVE -> {
                        val payload = enqueuer.decodeResolve(next.payloadJson)
                        api.resolveQuestion(
                            questionId = questionId,
                            resolution = payload.resolution,
                            apiKey = apiKey,
                            questionType = payload.questionType,
                            optionId = payload.optionId,
                        )
                        dao.delete(next.id)
                    }
                    PendingMutationEntity.TYPE_EDIT -> {
                        val payload = enqueuer.decodeEdit(next.payloadJson)
                        api.editQuestion(
                            questionId = questionId,
                            title = payload.title,
                            resolveBy = payload.resolveByEpochMs?.toIsoLocalDate(),
                            notes = payload.notes,
                            apiKey = apiKey,
                        )
                        dao.delete(next.id)
                    }
                    PendingMutationEntity.TYPE_DELETE -> {
                        api.deleteQuestion(questionId)
                        dao.delete(next.id)
                    }
                    PendingMutationEntity.TYPE_SET_SHARED -> {
                        val payload = enqueuer.decodeSetShared(next.payloadJson)
                        api.setSharedPublicly(
                            questionId = questionId,
                            sharedPublicly = payload.sharedPublicly,
                            unlisted = payload.unlisted,
                            apiKey = apiKey,
                        )
                        dao.delete(next.id)
                    }
                    PendingMutationEntity.TYPE_ADD_COMMENT -> {
                        val payload = enqueuer.decodeAddComment(next.payloadJson)
                        api.addComment(questionId, payload.comment, apiKey)
                        dao.delete(next.id)
                    }
                    else -> {
                        dao.markErrored(next.id, "Unknown type: ${next.type}")
                    }
                }
                drainedAny = true
            } catch (e: IOException) {
                dao.markPending(next.id)
                return Outcome.RETRY
            } catch (e: HttpException) {
                val nextAttempt = next.attemptCount + 1
                if (nextAttempt >= MAX_HTTP_ATTEMPTS) {
                    dao.markErrored(next.id, "HTTP ${e.code()}: ${e.message()}")
                } else {
                    dao.markPendingAfterAttempt(next.id, "HTTP ${e.code()}: ${e.message()}")
                    return Outcome.RETRY
                }
            } catch (e: Exception) {
                dao.markErrored(next.id, e.message)
            }
        }
        // After draining, refresh once so the local cache reflects any side
        // effects of the API calls (server-canonical forecasts, comments, etc.).
        // Best-effort: a refresh failure shouldn't fail the worker.
        if (drainedAny) {
            runCatching { repository.refresh() }
        }
        return Outcome.SUCCESS
    }

    private suspend fun syncCreate(mutation: PendingMutationEntity) {
        val payload = enqueuer.decodeCreate(mutation.payloadJson)

        // Defend against duplicating server-side: if a previous attempt of this
        // mutation already created the question on the server (e.g. UNIQUE
        // constraint failure during reconciliation, then user tapped Retry),
        // skip the API call and reconcile against the existing server row.
        val preExisting = questionDao.findCreatedNear(
            title = payload.title,
            aroundEpochMs = mutation.createdAtEpochMs,
            windowMs = CREATE_MATCH_WINDOW_MS,
        )

        val realRow: dev.russell.fatebook.data.local.QuestionEntity = if (preExisting != null) {
            preExisting
        } else {
            val url = api.createQuestion(
                payload.title,
                payload.resolveByEpochMs.toIsoLocalDate(),
                payload.forecast,
                payload.tags.takeIf { it.isNotEmpty() },
            )
            repository.refresh()

            // Find the server row through tiered fallbacks. Url-id parsing is the
            // most reliable; everything below it is a defensive backup in case the
            // URL format ever changes.
            val parsedId = extractServerIdFromUrl(url)
            (parsedId?.let { questionDao.getById(it) })
                ?: questionDao.findByUrl(url.trim().removeSuffix("/"))
                ?: questionDao.findCreatedNear(
                    title = payload.title,
                    aroundEpochMs = mutation.createdAtEpochMs,
                    windowMs = CREATE_MATCH_WINDOW_MS,
                )
                ?: questionDao.findMostRecentNonLocalNear(
                    aroundEpochMs = mutation.createdAtEpochMs,
                    windowMs = CREATE_MATCH_WINDOW_MS,
                )
                ?: run {
                    val recent = questionDao.recentNonLocal(5).joinToString { "${it.id}|${it.url}" }
                    throw IllegalStateException(
                        "createQuestion succeeded (url=$url, parsedId=$parsedId) but no server row matched. " +
                            "Recent cache: [$recent]",
                    )
                }
        }

        // The server row is already in the cache from refresh(). Drop the local-id
        // row (FK CASCADE removes its child forecasts/comments) and remap any
        // queued follow-up mutations to point at the server id.
        //
        // We deliberately DON'T try to rename the local-id row to the server id
        // here — the server row already exists at that PK and renaming would
        // hit a UNIQUE constraint. Any locally-added forecasts on the local id
        // already have their own ADD_FORECAST mutations queued; those will run
        // next (now retargeted to the server id) and push the data server-side.
        transactor.transact {
            questionDao.deleteById(mutation.questionLocalId)
            dao.rewriteQuestionId(mutation.questionLocalId, realRow.id)
            dao.delete(mutation.id)
        }
        tempIdToRealId[mutation.questionLocalId] = realRow.id
    }

    private fun remap(questionLocalId: String): String =
        tempIdToRealId[questionLocalId] ?: questionLocalId

    private fun Long.toIsoLocalDate(): String =
        java.time.Instant.ofEpochMilli(this)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)

    companion object {
        const val MAX_HTTP_ATTEMPTS = 5
        const val CREATE_MATCH_WINDOW_MS = 5L * 60 * 1000

        /**
         * Extract the question id from a Fatebook URL. URLs look like
         * `https://fatebook.io/q/<slug>--<cuid>`. Returns null if no `--`
         * separator is present or the trailing portion doesn't look like an id.
         */
        internal fun extractServerIdFromUrl(url: String): String? {
            val trimmed = url.trim().removeSuffix("/")
            val tail = trimmed.substringAfterLast("--", missingDelimiterValue = "")
            return tail.takeIf {
                it.isNotBlank() &&
                    !it.contains('/') &&
                    !it.contains(' ') &&
                    it.length in 8..40 &&
                    it.all { c -> c.isLetterOrDigit() }
            }
        }
    }
}
