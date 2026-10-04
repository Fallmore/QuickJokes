package com.unfallenmore.quickjokes.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Расчёт времени до следующего ежедневного напоминания.
 *
 * Чистая логика (без Android) — юнит-тесты с фиксированным моментом времени.
 */
object ReminderTime {

    /**
     * Сколько миллисекунд до ближайшего наступления [hour]:minute
     * после момента [nowMillis] (в зоне [zone]).
     *
     * Если текущий момент уже прошло время напоминания — считаем до завтра.
     * Ровно текущее время трактуется как «прошло» → завтра.
     */
    fun nextDelayMillis(
        nowMillis: Long,
        hour: Int,
        minute: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        require(hour in 0..23) { "Час вне диапазона: $hour" }
        require(minute in 0..59) { "Минута вне диапазона: $minute" }

        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        var next = now.toLocalDate().atTime(hour, minute)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        return Duration.between(now, next).toMillis()
    }
}
