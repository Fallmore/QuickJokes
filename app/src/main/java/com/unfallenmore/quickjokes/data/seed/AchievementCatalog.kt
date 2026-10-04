package com.unfallenmore.quickjokes.data.seed

import com.unfallenmore.quickjokes.data.model.AchievementType

/**
 * Каталог достижений. Сидится в Room при каждом запуске (INSERT OR IGNORE),
 * поэтому добавление новых достижений в обновлениях безопасно:
 * существующие не перезаписываются, новые добавляются.
 *
 * Порог ([Achievement.threshold]) — целевое значение, сравнивается как
 * «факт >= threshold».
 */
data class AchievementDef(
    val code: String,
    val title: String,
    val description: String,
    val type: AchievementType,
    val threshold: Int,
    val rewardCoins: Int,
    val sortOrder: Int,
)

object AchievementCatalog {

    val all: List<AchievementDef> = listOf(
        // Первая шутка
        AchievementDef(
            code = "first_joke",
            title = "Первая шутка",
            description = "Придумай свою первую шутку",
            type = AchievementType.FIRST_JOKE,
            threshold = 1,
            rewardCoins = 10,
            sortOrder = 1,
        ),

        // Шуток в минуту
        AchievementDef(
            code = "jpm_1",
            title = "Разогрев",
            description = "Достигни 1 шутки в минуту",
            type = AchievementType.JOKES_PER_MINUTE,
            threshold = 1,
            rewardCoins = 10,
            sortOrder = 10,
        ),
        AchievementDef(
            code = "jpm_5",
            title = "Быстрый старт",
            description = "Достигни 5 шуток в минуту",
            type = AchievementType.JOKES_PER_MINUTE,
            threshold = 5,
            rewardCoins = 30,
            sortOrder = 11,
        ),
        AchievementDef(
            code = "jpm_10",
            title = "Мастер темпа",
            description = "Достигни 10 шуток в минуту",
            type = AchievementType.JOKES_PER_MINUTE,
            threshold = 10,
            rewardCoins = 75,
            sortOrder = 12,
        ),

        // Шуток за одну сессию
        AchievementDef(
            code = "session_5",
            title = "Пятёрка",
            description = "5 шуток за одну сессию",
            type = AchievementType.JOKES_PER_SESSION,
            threshold = 5,
            rewardCoins = 20,
            sortOrder = 20,
        ),
        AchievementDef(
            code = "session_10",
            title = "Десятка",
            description = "10 шуток за одну сессию",
            type = AchievementType.JOKES_PER_SESSION,
            threshold = 10,
            rewardCoins = 40,
            sortOrder = 21,
        ),
        AchievementDef(
            code = "session_20",
            title = "Двадцатка",
            description = "20 шуток за одну сессию",
            type = AchievementType.JOKES_PER_SESSION,
            threshold = 20,
            rewardCoins = 100,
            sortOrder = 22,
        ),

        // Всего шуток
        AchievementDef(
            code = "total_50",
            title = "Полсотни",
            description = "50 шуток всего",
            type = AchievementType.TOTAL_JOKES,
            threshold = 50,
            rewardCoins = 30,
            sortOrder = 30,
        ),
        AchievementDef(
            code = "total_100",
            title = "Сотня",
            description = "100 шуток всего",
            type = AchievementType.TOTAL_JOKES,
            threshold = 100,
            rewardCoins = 60,
            sortOrder = 31,
        ),
        AchievementDef(
            code = "total_500",
            title = "Шутник",
            description = "500 шуток всего",
            type = AchievementType.TOTAL_JOKES,
            threshold = 500,
            rewardCoins = 200,
            sortOrder = 32,
        ),

        // Streak
        AchievementDef(
            code = "streak_3",
            title = "Три дня",
            description = "Заходи 3 дня подряд",
            type = AchievementType.STREAK_DAYS,
            threshold = 3,
            rewardCoins = 25,
            sortOrder = 40,
        ),
        AchievementDef(
            code = "streak_7",
            title = "Неделя",
            description = "Заходи 7 дней подряд",
            type = AchievementType.STREAK_DAYS,
            threshold = 7,
            rewardCoins = 75,
            sortOrder = 41,
        ),
        AchievementDef(
            code = "streak_30",
            title = "Месяц",
            description = "Заходи 30 дней подряд",
            type = AchievementType.STREAK_DAYS,
            threshold = 30,
            rewardCoins = 300,
            sortOrder = 42,
        ),

        // Пройдено тем
        AchievementDef(
            code = "topics_5",
            title = "Пятиборец",
            description = "Пройди 5 разных тем",
            type = AchievementType.TOPICS_PLAYED,
            threshold = 5,
            rewardCoins = 40,
            sortOrder = 50,
        ),
        AchievementDef(
            code = "topics_10",
            title = "Знаток",
            description = "Пройди 10 разных тем",
            type = AchievementType.TOPICS_PLAYED,
            threshold = 10,
            rewardCoins = 80,
            sortOrder = 51,
        ),
        AchievementDef(
            code = "topics_20",
            title = "Эрудит",
            description = "Пройди 20 разных тем",
            type = AchievementType.TOPICS_PLAYED,
            threshold = 20,
            rewardCoins = 150,
            sortOrder = 52,
        ),

        // Свои темы
        AchievementDef(
            code = "custom_topic_1",
            title = "Автор",
            description = "Создай свою первую тему",
            type = AchievementType.CUSTOM_TOPICS_CREATED,
            threshold = 1,
            rewardCoins = 20,
            sortOrder = 60,
        ),

        // Разблокировки
        AchievementDef(
            code = "unlock_10",
            title = "Коллекционер",
            description = "Разблокируй 10 тем",
            type = AchievementType.TOPICS_UNLOCKED,
            threshold = 10,
            rewardCoins = 100,
            sortOrder = 70,
        ),
    )
}
