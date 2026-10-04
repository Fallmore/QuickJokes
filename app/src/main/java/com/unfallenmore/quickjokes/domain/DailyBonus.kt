package com.unfallenmore.quickjokes.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Ежедневный бонус: размер серии и начисленные монеты.
 */
data class DailyBonus(
    val streak: Int,
    val coins: Int,
    val isSevenDayMilestone: Boolean,
)

/**
 * Чистая логика ежедневного бонуса.
 *
 * Считаем по календарной дате устройства: если наступил новый день —
 * серия продолжается (вчера был вход) или сбрасывается в 1.
 * Каждый 7-й день серии даёт повышенную награду.
 */
object DailyBonusCalculator {

    /** Базовый бонус за вход. */
    const val BASE_BONUS = 10

    /** Прибавка за каждый день серии сверх 3-го. */
    const val STEP = 2

    /** День, с которого начинается прогрессия (серия >= 3). */
    const val PROGRESSION_START_DAY = 3

    /** День серии с повышенной наградой. */
    const val MILESTONE_DAYS = 7

    /** Награда за milestone-день. */
    const val MILESTONE_BONUS = 75

    /**
     * Рассчитать бонус за [today].
     *
     * @param lastLoginDateIso дата последнего входа (ISO yyyy-MM-dd) или null
     * @param currentStreak    текущая серия в профиле
     * @return null, если бонус за этот день уже получен
     */
    fun calculate(
        lastLoginDateIso: String?,
        currentStreak: Int,
        today: LocalDate,
    ): DailyBonus? {
        val todayIso = today.iso()
        if (lastLoginDateIso == todayIso) return null

        val yesterdayIso = today.minusDays(1).iso()
        val streak = if (lastLoginDateIso == yesterdayIso) {
            // Пропущен день — серия начинается заново
            if (currentStreak > 0) currentStreak + 1 else 1
        } else {
            1
        }

        return DailyBonus(
            streak = streak,
            coins = coinsFor(streak),
            isSevenDayMilestone = streak % MILESTONE_DAYS == 0,
        )
    }

    /** Размер бонуса для известной серии (без проверки дат). */
    fun coinsFor(streak: Int): Int = when {
        streak > 0 && streak % MILESTONE_DAYS == 0 -> MILESTONE_BONUS
        streak >= PROGRESSION_START_DAY -> BASE_BONUS + (streak - PROGRESSION_START_DAY) * STEP
        else -> BASE_BONUS
    }

    private fun LocalDate.iso(): String = format(DateTimeFormatter.ISO_LOCAL_DATE)
}
