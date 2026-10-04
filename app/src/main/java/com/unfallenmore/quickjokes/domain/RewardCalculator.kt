package com.unfallenmore.quickjokes.domain

import com.unfallenmore.quickjokes.data.model.Difficulty
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Чистая доменная логика расчёта награды и шуток в минуту.
 * Никаких зависимостей от Android — покрывается unit-тестами.
 */
object RewardCalculator {

    /**
     * Ниже этого порога бонус за JPM не начисляется: на очень коротких
     * сессиях 1 шутка за 5 секунд дала бы 12 JPM и раздула бы и награду,
     * и достижения.
     */
    const val JPM_MIN_ELAPSED_SEC = 15

    /** Пороги JPM задаёт сложность ([Difficulty.jpmTier1]/[jpmTier2]). */
    private const val JPM_TIER_1_BONUS = 5
    private const val JPM_TIER_2_BONUS = 10

    /** Шуток в минуту. 0 при нулевой/отрицательной фактической длительности. */
    fun jokesPerMinute(jokesCount: Int, elapsedSec: Int): Double {
        if (jokesCount <= 0 || elapsedSec <= 0) return 0.0
        return jokesCount * 60.0 / elapsedSec
    }

    /**
     * Итоговая награда:
     *  база за сложность + 1 монета за каждую шутку + бонус за темп + премия
     *  за достижение цели.
     *
     * База пропорциональна отработанному времени ([proratedBase]): за досрочный
     * выход платим долю, а не всю сумму. Сессия без единой шутки не оплачивается
     * вовсе — забег «зайти и выйти» ничего не приносит.
     *
     * @param plannedSec планируемая длительность сессии, сек.
     */
    fun rewardCoins(
        difficulty: Difficulty,
        jokesCount: Int,
        elapsedSec: Int,
        plannedSec: Int,
    ): Int {
        if (jokesCount <= 0) return 0
        val base = proratedBase(difficulty, elapsedSec, plannedSec)
        val perJoke = jokesCount
        val tempo = jpmBonus(jokesCount, elapsedSec, difficulty)
        val goal = goalBonus(difficulty, jokesCount, elapsedSec, plannedSec)
        return base + perJoke + tempo + goal
    }

    /**
     * База за сложность с поправкой на отработанное время:
     * полная — если сессия доиграна до конца, иначе доля elapsed/planned.
     *
     * @return 0, если время не отработано вообще или план не задан.
     */
    fun proratedBase(difficulty: Difficulty, elapsedSec: Int, plannedSec: Int): Int {
        if (plannedSec <= 0 || elapsedSec <= 0) return 0
        if (elapsedSec >= plannedSec) return difficulty.baseReward
        return (difficulty.baseReward.toDouble() * elapsedSec / plannedSec).roundToInt()
    }

    /**
     * Бонус за темп: пороги темпа задаёт сложность (на сложном планка выше),
     * 0, если сессия слишком короткая для честной оценки.
     */
    fun jpmBonus(jokesCount: Int, elapsedSec: Int, difficulty: Difficulty): Int {
        if (elapsedSec < JPM_MIN_ELAPSED_SEC) return 0
        val jpm = jokesPerMinute(jokesCount, elapsedSec)
        return when {
            jpm >= difficulty.jpmTier2 -> JPM_TIER_2_BONUS
            jpm >= difficulty.jpmTier1 -> JPM_TIER_1_BONUS
            else -> 0
        }
    }

    /**
     * Цель сессии в шутках: целевой темп ([Difficulty.targetJpm]) × минуты.
     * Округление вверх — «недоед» не считается достигнутым.
     */
    fun goalCount(difficulty: Difficulty, plannedSec: Int): Int {
        if (plannedSec <= 0 || difficulty.targetJpm <= 0) return 0
        return ceil(difficulty.targetJpm * plannedSec / 60.0).toInt()
    }

    /**
     * Премия за достижение цели сессии. Цель — про выработку, поэтому она
     * платится и при досрочном выходе, но только если отработано достаточно
     * времени для честной оценки (как и бонусу за темп).
     */
    fun goalBonus(
        difficulty: Difficulty,
        jokesCount: Int,
        elapsedSec: Int,
        plannedSec: Int,
    ): Int {
        if (elapsedSec < JPM_MIN_ELAPSED_SEC) return 0
        val goal = goalCount(difficulty, plannedSec)
        if (goal <= 0) return 0
        return if (jokesCount >= goal) difficulty.goalBonus else 0
    }

    /** JPM для сохранения в сессии; округление до 2 знаков. */
    fun roundedJpm(jokesCount: Int, elapsedSec: Int): Double =
        (jokesPerMinute(jokesCount, elapsedSec) * 100).roundToInt() / 100.0

    /** Фактическая длительность сессии в секундах (не меньше 0). */
    fun elapsedSeconds(startMillis: Long, endMillis: Long): Int {
        val diff = endMillis - startMillis
        if (diff <= 0L) return 0
        return (diff / 1000L).toInt()
    }
}
