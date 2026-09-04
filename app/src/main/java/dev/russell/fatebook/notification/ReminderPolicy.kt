package dev.russell.fatebook.notification

import java.time.Instant
import java.time.ZonedDateTime

/** Pure decisions behind [ReminderWorker], kept free of Android so they unit-test directly. */
object ReminderPolicy {

    /** Questions due within this many days get the "resolving soon" notification. */
    const val RESOLVING_SOON_DAYS = 3

    /**
     * True if a prediction was made on the same local calendar day as [now].
     * [lastPredictionEpochMs] is stamped by in-app create/forecast only, so a
     * prediction made on the website doesn't count — the reminder may still
     * fire on such a day.
     */
    fun predictedToday(lastPredictionEpochMs: Long, now: ZonedDateTime): Boolean {
        if (lastPredictionEpochMs <= 0L) return false
        val last = Instant.ofEpochMilli(lastPredictionEpochMs).atZone(now.zone).toLocalDate()
        return last == now.toLocalDate()
    }
}
