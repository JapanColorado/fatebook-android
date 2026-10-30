package dev.russell.fatebook.notification

import java.time.Instant
import java.time.ZonedDateTime

/** Pure decisions behind [ReminderTick] and [ReminderScheduler], kept free of Android so they unit-test directly. */
object ReminderPolicy {

    /** Questions due within this many days get the "resolving soon" notification. */
    const val RESOLVING_SOON_DAYS = 3

    /**
     * The next time the wall clock in [now]'s zone reads [hour]:[minute], strictly
     * after [now]. Computed per day rather than by adding 24 hours, so it stays on
     * the chosen time across DST changes.
     */
    fun nextOccurrence(now: ZonedDateTime, hour: Int, minute: Int): ZonedDateTime {
        val today = now.toLocalDate().atTime(hour, minute).atZone(now.zone)
        return if (today.isAfter(now)) {
            today
        } else {
            now.toLocalDate().plusDays(1).atTime(hour, minute).atZone(now.zone)
        }
    }

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
