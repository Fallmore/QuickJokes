package com.unfallenmore.quickjokes.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyBonusCalculatorTest {

    private val day: LocalDate = LocalDate.of(2026, 10, 3)

    // --- calculate: серии ---

    @Test
    fun `first ever login gives streak 1 and base bonus`() {
        val bonus = DailyBonusCalculator.calculate(null, 0, day)

        assertNotNull(bonus)
        assertEquals(1, bonus!!.streak)
        assertEquals(DailyBonusCalculator.BASE_BONUS, bonus.coins)
        assertFalse(bonus.isSevenDayMilestone)
    }

    @Test
    fun `bonus for the same day is not given twice`() {
        val bonus = DailyBonusCalculator.calculate("2026-10-03", 5, day)
        assertNull(bonus)
    }

    @Test
    fun `streak continues when yesterday was a login`() {
        val bonus = DailyBonusCalculator.calculate("2026-10-02", 4, day)

        assertEquals(5, bonus!!.streak)
        assertFalse(bonus.isSevenDayMilestone)
    }

    @Test
    fun `streak resets after a missed day`() {
        val bonus = DailyBonusCalculator.calculate("2026-10-01", 9, day)

        assertEquals(1, bonus!!.streak)
        assertEquals(DailyBonusCalculator.BASE_BONUS, bonus.coins)
    }

    @Test
    fun `streak resets when login was a week ago`() {
        val bonus = DailyBonusCalculator.calculate("2026-09-26", 6, day)
        assertEquals(1, bonus!!.streak)
    }

    @Test
    fun `streak continues across month boundary`() {
        val firstFebruary = LocalDate.of(2026, 2, 1)
        val bonus = DailyBonusCalculator.calculate("2026-01-31", 6, firstFebruary)

        assertEquals(7, bonus!!.streak)
        assertTrue(bonus.isSevenDayMilestone)
        assertEquals(DailyBonusCalculator.MILESTONE_BONUS, bonus.coins)
    }

    @Test
    fun `streak continues across year boundary`() {
        val newYear = LocalDate.of(2027, 1, 1)
        val bonus = DailyBonusCalculator.calculate("2026-12-31", 2, newYear)
        assertEquals(3, bonus!!.streak)
    }

    @Test
    fun `broken stored streak with yesterday login starts from 1`() {
        val bonus = DailyBonusCalculator.calculate("2026-10-02", 0, day)
        assertEquals(1, bonus!!.streak)
    }

    // --- calculate: монеты ---

    @Test
    fun `coins for days 1 and 2 are base`() {
        assertEquals(DailyBonusCalculator.BASE_BONUS, DailyBonusCalculator.coinsFor(1))
        assertEquals(DailyBonusCalculator.BASE_BONUS, DailyBonusCalculator.coinsFor(2))
    }

    @Test
    fun `coins grow by step starting from day 3`() {
        assertEquals(10, DailyBonusCalculator.coinsFor(3))
        assertEquals(12, DailyBonusCalculator.coinsFor(4))
        assertEquals(14, DailyBonusCalculator.coinsFor(5))
        assertEquals(16, DailyBonusCalculator.coinsFor(6))
    }

    @Test
    fun `every seventh day pays milestone bonus`() {
        assertEquals(DailyBonusCalculator.MILESTONE_BONUS, DailyBonusCalculator.coinsFor(7))
        assertEquals(DailyBonusCalculator.MILESTONE_BONUS, DailyBonusCalculator.coinsFor(14))
        assertEquals(DailyBonusCalculator.MILESTONE_BONUS, DailyBonusCalculator.coinsFor(21))
    }

    @Test
    fun `progression keeps growing after a milestone day`() {
        // 8-й день: milestone не срабатывает, прогрессия продолжается
        assertEquals(20, DailyBonusCalculator.coinsFor(8))
        assertEquals(22, DailyBonusCalculator.coinsFor(9))
        // и всё равно меньше, чем в milestone-день
        assertTrue(DailyBonusCalculator.coinsFor(8) < DailyBonusCalculator.MILESTONE_BONUS)
    }

    @Test
    fun `milestone overrides the progression formula`() {
        // Без milestone 7-й день дал бы 10 + (7-3)*2 = 18
        assertTrue(DailyBonusCalculator.coinsFor(7) != 18)
    }

    // --- calculate: флаг milestone ---

    @Test
    fun `milestone flag set only on multiples of 7`() {
        val onSeventh = DailyBonusCalculator.calculate("2026-10-02", 6, day)
        assertTrue(onSeventh!!.isSevenDayMilestone)

        val firstDay = DailyBonusCalculator.calculate("2026-10-02", 0, day)
        assertEquals(1, firstDay!!.streak)
        assertFalse(firstDay.isSevenDayMilestone)
    }

    @Test
    fun `calculate result matches coinsFor for a long streak`() {
        // Вчера вход, streak 20 → сегодня 21-й день (milestone)
        val bonus = DailyBonusCalculator.calculate("2026-10-02", 20, day)
        assertEquals(21, bonus!!.streak)
        assertEquals(DailyBonusCalculator.coinsFor(21), bonus.coins)
        assertEquals(
            DailyBonusCalculator.MILESTONE_BONUS,
            bonus.coins,
        )
        assertFalse(bonus.streak <= 0)
    }
}
