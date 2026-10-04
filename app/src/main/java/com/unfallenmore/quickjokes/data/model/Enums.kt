package com.unfallenmore.quickjokes.data.model

/**
 * Тип темы определяет, чем пользователь «подпитывается» во время сессии:
 * текстовым описанием, картинкой или описанием ситуации.
 */
enum class TopicType {
    TEXT,
    IMAGE,
    SITUATION;

    companion object {
        fun fromString(value: String?): TopicType =
            entries.firstOrNull { it.name == value } ?: TEXT
    }
}

/**
 * Сложность сессии — главный «крутильный» параметр настройки:
 *  - [durationSec] — фиксированное время сессии;
 *  - [baseReward] — базовая награда за полностью отработанное время;
 *  - [targetJpm] — целевой темп, из него считается цель в шутках;
 *  - [goalBonus] — премия за достижение цели;
 *  - [jpmTier1]/[jpmTier2] — пороги темпа для бонуса (+5/+10),
 *    сложнее = выше планка.
 *
 * Монеты за каждую шутку (1) и размеры JPM-бонусов от сложности не зависят.
 */
enum class Difficulty(
    val baseReward: Int,
    val durationSec: Int,
    val targetJpm: Int,
    val goalBonus: Int,
    val jpmTier1: Int,
    val jpmTier2: Int,
) {
    EASY(baseReward = 5, durationSec = 120, targetJpm = 3, goalBonus = 3, jpmTier1 = 4, jpmTier2 = 8),
    MEDIUM(baseReward = 10, durationSec = 60, targetJpm = 4, goalBonus = 5, jpmTier1 = 5, jpmTier2 = 10),
    HARD(baseReward = 15, durationSec = 30, targetJpm = 6, goalBonus = 8, jpmTier1 = 7, jpmTier2 = 12);

    /** Название для интерфейса — приложение полностью на русском. */
    val ruLabel: String
        get() = when (this) {
            EASY -> "Легко"
            MEDIUM -> "Средне"
            HARD -> "Сложно"
        }

    companion object {
        fun fromString(value: String?): Difficulty =
            entries.firstOrNull { it.name == value } ?: EASY
    }
}

/** Режим ввода шуток в сессии. */
enum class InputMode {
    TEXT,
    VOICE,
    BOTH;

    companion object {
        fun fromString(value: String?): InputMode =
            entries.firstOrNull { it.name == value } ?: TEXT
    }
}

/** Как завершилась сессия. */
enum class SessionStatus {
    TIME_OUT,
    FINISHED_EARLY;

    companion object {
        fun fromString(value: String?): SessionStatus =
            entries.firstOrNull { it.name == value } ?: TIME_OUT
    }
}

/** Тип движения монет. */
enum class CoinTransactionType {
    REWARD,
    FIRST_TOPIC_COMPLETION,
    ACHIEVEMENT,
    DAILY_BONUS,
    TOPIC_UNLOCK,
    CUSTOM_TOPIC_PURCHASE;

    companion object {
        fun fromString(value: String?): CoinTransactionType =
            entries.firstOrNull { it.name == value } ?: REWARD
    }
}

/** Категория достижения — определяет, какой агрегат проверять. */
enum class AchievementType {
    /** Количество шуток за одну сессию. */
    JOKES_PER_SESSION,

    /** Шуток в минуту (лучший результат). */
    JOKES_PER_MINUTE,

    /** Всего шуток. */
    TOTAL_JOKES,

    /** Дней подряд (streak). */
    STREAK_DAYS,

    /** Сколько разных тем пройдено. */
    TOPICS_PLAYED,

    /** Собственных тем создано. */
    CUSTOM_TOPICS_CREATED,

    /** Тем разблокировано. */
    TOPICS_UNLOCKED,

    /** Факт: есть хотя бы одна шутка. */
    FIRST_JOKE;

    companion object {
        fun fromString(value: String?): AchievementType =
            entries.firstOrNull { it.name == value } ?: FIRST_JOKE
    }
}
