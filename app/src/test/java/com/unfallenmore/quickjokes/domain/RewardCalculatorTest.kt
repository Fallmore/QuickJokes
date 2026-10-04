package com.unfallenmore.quickjokes.domain

import com.unfallenmore.quickjokes.data.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Test

class RewardCalculatorTest {

    // --- jokesPerMinute ---

    @Test
    fun `jpm is zero when no jokes`() {
        assertEquals(0.0, RewardCalculator.jokesPerMinute(0, 60), 0.0001)
    }

    @Test
    fun `jpm is zero when elapsed is zero`() {
        assertEquals(0.0, RewardCalculator.jokesPerMinute(5, 0), 0.0001)
    }

    @Test
    fun `jpm is zero when elapsed is negative`() {
        assertEquals(0.0, RewardCalculator.jokesPerMinute(5, -10), 0.0001)
    }

    @Test
    fun `jpm for full minute equals joke count`() {
        assertEquals(7.0, RewardCalculator.jokesPerMinute(7, 60), 0.0001)
    }

    @Test
    fun `jpm scales for shorter sessions`() {
        assertEquals(12.0, RewardCalculator.jokesPerMinute(1, 5), 0.0001)
        assertEquals(6.0, RewardCalculator.jokesPerMinute(3, 30), 0.0001)
    }

    // --- rewardCoins ---

    @Test
    fun `reward is base plus one coin per joke`() {
        // 120 сек: цель 8 шуток не достигнута, темп 2/мин — бонусов нет
        val reward = RewardCalculator.rewardCoins(
            difficulty = Difficulty.MEDIUM,
            jokesCount = 4,
            elapsedSec = 120,
            plannedSec = 120,
        )
        assertEquals(14, reward)
    }

    @Test
    fun `reward includes goal premium`() {
        // 60 сек, цель 4 шутки достигнута; темп 4/мин — ниже порога 5
        val reward = RewardCalculator.rewardCoins(
            difficulty = Difficulty.MEDIUM,
            jokesCount = 4,
            elapsedSec = 60,
            plannedSec = 60,
        )
        // база 10 + 4 шутки + премия за цель 5
        assertEquals(19, reward)
    }

    @Test
    fun `tempo bonus stacks with goal premium`() {
        // Легко: 5 шуток за 60 сек = 5/мин (порог 4) и цель 3 достигнута
        val reward = RewardCalculator.rewardCoins(
            difficulty = Difficulty.EASY,
            jokesCount = 5,
            elapsedSec = 60,
            plannedSec = 60,
        )
        // база 5 + 5 шуток + темп 5 + цель 3
        assertEquals(18, reward)
    }

    @Test
    fun `reward includes tier two jpm bonus`() {
        // Легко: 8/мин — второй порог (8) достигнут
        val reward = RewardCalculator.rewardCoins(
            difficulty = Difficulty.EASY,
            jokesCount = 8,
            elapsedSec = 60,
            plannedSec = 60,
        )
        // база 5 + 8 шуток + темп 10 + цель 3
        assertEquals(26, reward)
    }

    @Test
    fun `no bonuses on very short sessions`() {
        val reward = RewardCalculator.rewardCoins(
            difficulty = Difficulty.EASY,
            jokesCount = 1,
            elapsedSec = 5,
            plannedSec = 5,
        )
        // 1 шутка за 5 сек = 12 JPM, но сессия короче порога 15 сек —
        // ни темп, ни премия за цель не считаются честными
        assertEquals(6, reward)
        assertEquals(0, RewardCalculator.jpmBonus(1, 5, Difficulty.EASY))
        assertEquals(0, RewardCalculator.goalBonus(Difficulty.EASY, 1, 5, 5))
    }

    @Test
    fun `reward for zero jokes is zero`() {
        // Пустая сессия не оплачивается ни при каких условиях
        assertEquals(0, RewardCalculator.rewardCoins(Difficulty.HARD, 0, 60, 60))
        assertEquals(0, RewardCalculator.rewardCoins(Difficulty.HARD, 0, 300, 300))
    }

    // --- jpmBonus: пороги темпа зависят от сложности ---

    @Test
    fun `tempo thresholds depend on difficulty`() {
        // jpm = 4: только порог легкой достигнут
        assertEquals(5, RewardCalculator.jpmBonus(4, 60, Difficulty.EASY))
        assertEquals(0, RewardCalculator.jpmBonus(4, 60, Difficulty.MEDIUM))
        assertEquals(0, RewardCalculator.jpmBonus(4, 60, Difficulty.HARD))

        // jpm = 10: легкий(8) и средний(10) — второй уровень, сложный(12) — первый
        assertEquals(10, RewardCalculator.jpmBonus(10, 60, Difficulty.EASY))
        assertEquals(10, RewardCalculator.jpmBonus(10, 60, Difficulty.MEDIUM))
        assertEquals(5, RewardCalculator.jpmBonus(10, 60, Difficulty.HARD))
    }

    @Test
    fun `jpm bonus allowed at threshold`() {
        // ровно на пороге второго уровня у легкой (8/мин)
        assertEquals(10, RewardCalculator.jpmBonus(8, 60, Difficulty.EASY))
        // 7.9/мин — ещё не второй уровень, только первый
        assertEquals(5, RewardCalculator.jpmBonus(79, 600, Difficulty.EASY))
    }

    // --- goalCount / goalBonus: цель сессии ---

    @Test
    fun `goal count follows difficulty pace and duration`() {
        assertEquals(6, RewardCalculator.goalCount(Difficulty.EASY, 120))
        assertEquals(4, RewardCalculator.goalCount(Difficulty.MEDIUM, 60))
        assertEquals(3, RewardCalculator.goalCount(Difficulty.HARD, 30))
        assertEquals(0, RewardCalculator.goalCount(Difficulty.EASY, 0))
    }

    @Test
    fun `goal count rounds up`() {
        // 3/мин × 30 сек = 1.5 => 2
        assertEquals(2, RewardCalculator.goalCount(Difficulty.EASY, 30))
        // 6/мин × 10 сек = 1 => 1
        assertEquals(1, RewardCalculator.goalCount(Difficulty.HARD, 10))
    }

    @Test
    fun `goal premium is paid only when the goal is reached`() {
        assertEquals(3, RewardCalculator.goalBonus(Difficulty.EASY, 6, 120, 120))
        assertEquals(0, RewardCalculator.goalBonus(Difficulty.EASY, 5, 120, 120))
    }

    @Test
    fun `goal premium requires enough elapsed time`() {
        // Сессия короче 15 сек — премия не считается
        assertEquals(0, RewardCalculator.goalBonus(Difficulty.EASY, 6, 10, 120))
        // Выработку можно закрыть и досрочно — время отработано, шутки есть
        assertEquals(8, RewardCalculator.goalBonus(Difficulty.HARD, 3, 20, 30))
    }

    // --- proratedBase (досрочное завершение) ---

    @Test
    fun `full session pays the full base`() {
        assertEquals(
            Difficulty.MEDIUM.baseReward,
            RewardCalculator.proratedBase(Difficulty.MEDIUM, 300, 300),
        )
        // таймер может сработать с опозданием — база всё равно полная
        assertEquals(
            Difficulty.MEDIUM.baseReward,
            RewardCalculator.proratedBase(Difficulty.MEDIUM, 320, 300),
        )
    }

    @Test
    fun `early finish pays only the share of worked time`() {
        // 60 из 300 сек => 1/5 базы (10 -> 2); плюс 4 шутки = 6
        assertEquals(
            6,
            RewardCalculator.rewardCoins(
                difficulty = Difficulty.MEDIUM,
                jokesCount = 4,
                elapsedSec = 60,
                plannedSec = 300,
            ),
        )
        assertEquals(2, RewardCalculator.proratedBase(Difficulty.MEDIUM, 60, 300))
    }

    @Test
    fun `prorated base rounds to nearest whole coin`() {
        // 15 * 150/300 = 7.5 => 8
        assertEquals(8, RewardCalculator.proratedBase(Difficulty.HARD, 150, 300))
        // 5 * 10/60 = 0.83 => 1
        assertEquals(1, RewardCalculator.proratedBase(Difficulty.EASY, 10, 60))
    }

    @Test
    fun `prorated base is zero when nothing was worked`() {
        assertEquals(0, RewardCalculator.proratedBase(Difficulty.HARD, 0, 300))
        assertEquals(0, RewardCalculator.proratedBase(Difficulty.HARD, -5, 300))
        assertEquals(0, RewardCalculator.proratedBase(Difficulty.HARD, 100, 0))
    }

    // --- roundedJpm ---

    @Test
    fun `jpm is rounded to two decimals`() {
        assertEquals(6.67, RewardCalculator.roundedJpm(2, 18), 0.001)
        assertEquals(0.0, RewardCalculator.roundedJpm(0, 60), 0.0001)
    }

    // --- elapsedSeconds ---

    @Test
    fun `elapsed counts real seconds`() {
        assertEquals(60, RewardCalculator.elapsedSeconds(1_000L, 61_000L))
    }

    @Test
    fun `elapsed never goes negative`() {
        assertEquals(0, RewardCalculator.elapsedSeconds(61_000L, 1_000L))
        assertEquals(0, RewardCalculator.elapsedSeconds(1_000L, 1_000L))
    }

    @Test
    fun `elapsed truncates milliseconds`() {
        assertEquals(1, RewardCalculator.elapsedSeconds(0L, 1_999L))
    }
}
