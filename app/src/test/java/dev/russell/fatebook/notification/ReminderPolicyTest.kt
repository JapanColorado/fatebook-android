package dev.russell.fatebook.notification

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderPolicyTest {

    private val zone = ZoneId.of("America/Denver")
    private val now = ZonedDateTime.of(2030, 6, 10, 9, 0, 0, 0, zone)

    @Test
    fun `never predicted (epoch 0) is not today`() {
        assertThat(ReminderPolicy.predictedToday(0L, now)).isFalse()
    }

    @Test
    fun `a prediction earlier the same local day counts`() {
        val earlier = now.withHour(0).withMinute(5).toInstant().toEpochMilli()
        assertThat(ReminderPolicy.predictedToday(earlier, now)).isTrue()
    }

    @Test
    fun `a prediction just before local midnight is yesterday`() {
        val lateYesterday = now.withHour(0).minusMinutes(1).toInstant().toEpochMilli()
        assertThat(ReminderPolicy.predictedToday(lateYesterday, now)).isFalse()
    }

    // --- nextOccurrence ---

    @Test
    fun `a time later today is today`() {
        val next = ReminderPolicy.nextOccurrence(now, 20, 30)
        assertThat(next).isEqualTo(now.withHour(20).withMinute(30))
    }

    @Test
    fun `a time already passed today is tomorrow`() {
        val next = ReminderPolicy.nextOccurrence(now, 8, 0)
        assertThat(next).isEqualTo(now.plusDays(1).withHour(8))
    }

    @Test
    fun `the alarm firing exactly on time arms tomorrow, not itself`() {
        val next = ReminderPolicy.nextOccurrence(now, 9, 0)
        assertThat(next).isEqualTo(now.plusDays(1))
    }

    @Test
    fun `stays on the wall-clock time across the fall DST change`() {
        // US clocks go back on 2030-11-03, making that day 25 hours long.
        val dayBefore = ZonedDateTime.of(2030, 11, 2, 9, 0, 0, 0, zone)
        val next = ReminderPolicy.nextOccurrence(dayBefore, 9, 0)
        assertThat(next.toLocalDateTime().toString()).isEqualTo("2030-11-03T09:00")
        assertThat(Duration.between(dayBefore, next)).isEqualTo(Duration.ofHours(25))
    }

    @Test
    fun `stays on the wall-clock time across the spring DST change`() {
        // US clocks go forward on 2030-03-10, making that day 23 hours long.
        val dayBefore = ZonedDateTime.of(2030, 3, 9, 9, 0, 0, 0, zone)
        val next = ReminderPolicy.nextOccurrence(dayBefore, 9, 0)
        assertThat(next.toLocalDateTime().toString()).isEqualTo("2030-03-10T09:00")
        assertThat(Duration.between(dayBefore, next)).isEqualTo(Duration.ofHours(23))
    }

    @Test
    fun `a time skipped by DST fires just after the gap`() {
        val dayBefore = ZonedDateTime.of(2030, 3, 9, 12, 0, 0, 0, zone)
        val next = ReminderPolicy.nextOccurrence(dayBefore, 2, 30)
        assertThat(next.toLocalDateTime().toString()).isEqualTo("2030-03-10T03:30")
    }
}
