package com.unfallenmore.quickjokes.domain

import com.unfallenmore.quickjokes.data.model.AchievementType

/**
 * Факты, по которым проверяются условия достижений.
 * Считаются из агрегатов, а не хранятся дублем — источник истины один.
 */
data class AchievementFacts(
    val totalJokes: Int,
    val bestJpm: Double,
    val bestJokesPerSession: Int,
    val streakDays: Int,
    val topicsPlayed: Int,
    val customTopicsCreated: Int,
    val topicsUnlocked: Int,
) {
    companion object {
        /** Пустые факты — для тестов и состояния «нет данных». */
        val EMPTY = AchievementFacts(
            totalJokes = 0,
            bestJpm = 0.0,
            bestJokesPerSession = 0,
            streakDays = 0,
            topicsPlayed = 0,
            customTopicsCreated = 0,
            topicsUnlocked = 0,
        )
    }
}

/** Выполнено ли условие достижения при заданных фактах. */
fun AchievementType.isSatisfiedBy(facts: AchievementFacts, threshold: Int): Boolean =
    when (this) {
        AchievementType.FIRST_JOKE -> facts.totalJokes >= 1
        AchievementType.TOTAL_JOKES -> facts.totalJokes >= threshold
        AchievementType.JOKES_PER_SESSION -> facts.bestJokesPerSession >= threshold
        AchievementType.JOKES_PER_MINUTE -> facts.bestJpm >= threshold
        AchievementType.STREAK_DAYS -> facts.streakDays >= threshold
        AchievementType.TOPICS_PLAYED -> facts.topicsPlayed >= threshold
        AchievementType.CUSTOM_TOPICS_CREATED -> facts.customTopicsCreated >= threshold
        AchievementType.TOPICS_UNLOCKED -> facts.topicsUnlocked >= threshold
    }

/**
 * Прогресс достижения для отображения: доля от порога 0..1.
 * Для типов, где порог — «хотя бы раз», progress = факт/порог.
 */
fun AchievementType.progressFractionOf(facts: AchievementFacts, threshold: Int): Float {
    if (threshold <= 0) return 0f
    val current: Float = when (this) {
        AchievementType.FIRST_JOKE -> facts.totalJokes.toFloat()
        AchievementType.TOTAL_JOKES -> facts.totalJokes.toFloat()
        AchievementType.JOKES_PER_SESSION -> facts.bestJokesPerSession.toFloat()
        AchievementType.JOKES_PER_MINUTE -> facts.bestJpm.toFloat()
        AchievementType.STREAK_DAYS -> facts.streakDays.toFloat()
        AchievementType.TOPICS_PLAYED -> facts.topicsPlayed.toFloat()
        AchievementType.CUSTOM_TOPICS_CREATED -> facts.customTopicsCreated.toFloat()
        AchievementType.TOPICS_UNLOCKED -> facts.topicsUnlocked.toFloat()
    }
    return (current / threshold).coerceIn(0f, 1f)
}
