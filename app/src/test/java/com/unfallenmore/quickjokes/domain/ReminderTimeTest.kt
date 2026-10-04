package com.unfallenmore.quickjokes.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderTimeTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun at(hour: Int, minute: Int, second: Int = 0): Long =
        LocalDateTime.of(2026, 10, 4, hour, minute, second)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    @Test
    fun `later the same day counts hours to target`() {
        // 10:00 → 19:00 = 9 часов
        val delay = ReminderTime.nextDelayMillis(at(10, 0), hour = 19, minute = 0, zone = zone)
        assertEquals(9 * 3_600_000L, delay)
    }

    @Test
    fun `target already passed today counts to tomorrow`() {
        // 20:00 → 19:00 завтра = 23 часа
        val delay = ReminderTime.nextDelayMillis(at(20, 0), hour = 19, minute = 0, zone = zone)
        assertEquals(23 * 3_600_000L, delay)
    }

    @Test
    fun `exact target time treated as passed`() {
        val delay = ReminderTime.nextDelayMillis(at(19, 0), hour = 19, minute = 0, zone = zone)
        assertEquals(24 * 3_600_000L, delay)
    }

    @Test
    fun `minutes and seconds precision kept`() {
        // 18:59:30 → 19:00 = 30 секунд
        val delay = ReminderTime.nextDelayMillis(at(18, 59, 30), hour = 19, minute = 0, zone = zone)
        assertEquals(30_000L, delay)
    }

    @Test
    fun `non default time works`() {
        // 08:00 → 08:30 = 30 минут
        val delay = ReminderTime.nextDelayMillis(at(8, 0), hour = 8, minute = 30, zone = zone)
        assertEquals(30 * 60_000L, delay)
    }

    @Test
    fun `out of range time rejected`() {
        val now = at(10, 0)
        assertThrows(IllegalArgumentException::class.java) {
            ReminderTime.nextDelayMillis(now, hour = 24, minute = 0, zone = zone)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReminderTime.nextDelayMillis(now, hour = 10, minute = 60, zone = zone)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReminderTime.nextDelayMillis(now, hour = -1, minute = 0, zone = zone)
        }
    }
}
