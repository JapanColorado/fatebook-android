package dev.russell.fatebook.notification

import com.google.common.truth.Truth.assertThat
import org.junit.Test
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
}
