package com.unfallenmore.quickjokes.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.unfallenmore.quickjokes.data.model.AchievementType

/**
 * Справочник достижений (сид из кода, не изменяется в рантайме).
 * [code] стабилен — на него ссылается [UserAchievement].
 */
@Entity(tableName = "achievements")
data class Achievement(
    @PrimaryKey
    val code: String,
    val title: String,
    val description: String,
    val type: AchievementType,
    /** Порог для типа: шуток, минут, дней и т.п. Для FIRST_JOKE = 1. */
    val threshold: Int,
    val rewardCoins: Int,
    /** Порядок отображения в сетке. */
    val sortOrder: Int = 0,
)

/**
 * Факт получения достижения.
 *
 * Прогресс НЕ храним дублем — он всегда вычисляется из агрегатов
 * (см. [com.unfallenmore.quickjokes.data.local.dao.StatsDao]), иначе
 * возможен рассинхрон между хранимым и фактическим прогрессом.
 */
@Entity(
    tableName = "user_achievements",
    primaryKeys = ["achievementCode"],
)
data class UserAchievement(
    val achievementCode: String,
    val unlockedAt: Long,
)
