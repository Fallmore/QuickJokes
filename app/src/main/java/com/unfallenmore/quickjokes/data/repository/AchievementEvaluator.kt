package com.unfallenmore.quickjokes.data.repository

import com.unfallenmore.quickjokes.data.local.dao.AchievementDao
import com.unfallenmore.quickjokes.data.local.dao.ProfileDao
import com.unfallenmore.quickjokes.data.local.dao.StatsDao
import com.unfallenmore.quickjokes.data.local.dao.TopicDao
import com.unfallenmore.quickjokes.data.local.entity.Achievement
import com.unfallenmore.quickjokes.data.local.entity.UserAchievement
import com.unfallenmore.quickjokes.data.model.AchievementType
import com.unfallenmore.quickjokes.domain.AchievementFacts
import com.unfallenmore.quickjokes.domain.isSatisfiedBy
import com.unfallenmore.quickjokes.domain.progressFractionOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Проверка условий достижений и выдача наград.
 *
 * Вызывается после завершённой сессии и при ежедневном входе.
 * Разблокировка + начисление монет за достижение выполняются атомарно.
 * Правила условий — чистые функции в domain/AchievementRules.kt.
 */
@Singleton
class AchievementEvaluator @Inject constructor(
    private val achievementDao: AchievementDao,
    private val profileDao: ProfileDao,
    private val statsDao: StatsDao,
    private val topicDao: TopicDao,
    private val ledger: CoinLedger,
) {
    /** Собрать текущие факты (вне транзакции — для отображения прогресса). */
    suspend fun collectFacts(): AchievementFacts {
        val profile = ledger.getOrCreateProfile()
        return AchievementFacts(
            totalJokes = statsDao.totalJokesAllTime(),
            bestJpm = statsDao.bestJokesPerMinute(),
            bestJokesPerSession = statsDao.bestJokesInSingleSession(),
            streakDays = profile.streak,
            topicsPlayed = statsDao.distinctTopicsPlayed(),
            customTopicsCreated = topicDao.countCustom(),
            topicsUnlocked = profile.topicsUnlocked,
        )
    }

    /**
     * Проверить все достижения и выдать новые (собственная транзакция).
     * @return только что полученные достижения (для показа на экране результатов).
     */
    suspend fun evaluateAndUnlock(): List<Achievement> =
        ledger.inTransaction { evaluateInTransaction() }

    /**
     * То же самое, но в уже открытой транзакции — используется, когда
     * начисления за сессию и достижения должны быть одним атомом.
     */
    suspend fun evaluateInTransaction(): List<Achievement> {
        val facts = collectFacts()
        val alreadyUnlocked = profileDao.unlockedCodes().toHashSet()
        val catalog = achievementDao.getCatalog()

        val newlyUnlocked = catalog.filter { def ->
            def.code !in alreadyUnlocked && def.type.isSatisfiedBy(facts, def.threshold)
        }

        for (def in newlyUnlocked) {
            profileDao.unlock(
                UserAchievement(
                    achievementCode = def.code,
                    unlockedAt = System.currentTimeMillis(),
                ),
            )
            ledger.earnInTransaction(
                amount = def.rewardCoins,
                type = com.unfallenmore.quickjokes.data.model.CoinTransactionType.ACHIEVEMENT,
                description = "Достижение «${def.title}»",
            )
        }

        return newlyUnlocked
    }

    /**
     * Прогресс достижения для отображения: доля от порога 0..1.
     * Логика — в domain/AchievementRules.kt.
     */
    fun progressFraction(type: AchievementType, facts: AchievementFacts, threshold: Int): Float =
        type.progressFractionOf(facts, threshold)
}
