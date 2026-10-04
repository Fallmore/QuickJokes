package com.unfallenmore.quickjokes.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Профиль пользователя — единственная строка (id = 1).
 * [coins] — кэш баланса, обновляется атомарно с [CoinTransaction].
 */
@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey
    val id: Int = SINGLE_ID,
    val coins: Int = 0,
    /** epoch millis последнего дня, за который начислен бонус; 0 = не было. */
    val lastBonusAt: Long = 0L,
    /** ISO-дата (yyyy-MM-dd) последнего дня бонуса, для проверки «новый день». */
    val lastLoginDate: String = "",
    /** Дней подряд входов. */
    val streak: Int = 0,
    val totalJokes: Int = 0,
    /** Сколько слотов под собственные темы куплено (первый дан бесплатно). */
    val customTopicSlots: Int = 1,
    /** Сколько тем разблокировано за монеты. */
    val topicsUnlocked: Int = 0,
    /** Онбординг показан. */
    val onboardingDone: Boolean = false,
) {
    companion object {
        const val SINGLE_ID = 1
    }
}

/**
 * История входов — нужна для календаря streak после наступления новых дней
 * (одного [UserProfile.lastLoginDate] для отрисовки «недели» недостаточно).
 */
@Entity(tableName = "login_days")
data class LoginDay(
    /** ISO-дата yyyy-MM-dd. */
    @PrimaryKey
    val date: String,
    val bonusCoins: Int,
    val streakAtDay: Int,
)
