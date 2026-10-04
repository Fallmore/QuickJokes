package com.unfallenmore.quickjokes.data.repository

import com.unfallenmore.quickjokes.data.local.dao.ProfileDao
import com.unfallenmore.quickjokes.data.local.entity.Achievement
import com.unfallenmore.quickjokes.data.local.entity.LoginDay
import com.unfallenmore.quickjokes.data.local.entity.UserProfile
import com.unfallenmore.quickjokes.data.model.CoinTransactionType
import com.unfallenmore.quickjokes.domain.DailyBonusCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Результат начисления ежедневного бонуса. */
sealed interface DailyBonusResult {
    /** Бонус уже получен сегодня. */
    data object AlreadyClaimed : DailyBonusResult

    data class Claimed(
        val coins: Int,
        val streak: Int,
        val isSevenDayMilestone: Boolean,
        val newAchievements: List<Achievement>,
    ) : DailyBonusResult
}

/**
 * Профиль: баланс, streak, ежедневный бонус, общие счётчики.
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val profileDao: ProfileDao,
    private val ledger: CoinLedger,
    private val achievements: AchievementEvaluator,
) {
    fun observeProfile(): Flow<UserProfile> =
        ledgerFlowOrEmpty()

    fun observeUnlockedAchievements(): Flow<Set<String>> =
        profileDao.observeUnlocked().map { list -> list.map { it.achievementCode }.toSet() }

    fun observeRecentLogins(limit: Int = 30): Flow<List<LoginDay>> =
        profileDao.observeRecentLogins(limit)

    suspend fun getProfile(): UserProfile = ledger.getOrCreateProfile()

    suspend fun getBalance(): Int = ledger.balance()

    suspend fun setOnboardingDone() {
        val profile = ledger.getOrCreateProfile()
        if (!profile.onboardingDone) {
            profileDao.upsertProfile(profile.copy(onboardingDone = true))
        }
    }

    /**
     * Ежедневный бонус. Правила расчёта — в [DailyBonusCalculator].
     */
    suspend fun claimDailyBonus(today: LocalDate = LocalDate.now()): DailyBonusResult =
        ledger.inTransaction {
            val profile = ledger.getOrCreateProfile()
            val todayIso = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

            val bonus = DailyBonusCalculator.calculate(
                lastLoginDateIso = profile.lastLoginDate,
                currentStreak = profile.streak,
                today = today,
            ) ?: return@inTransaction DailyBonusResult.AlreadyClaimed

            profileDao.upsertProfile(
                profile.copy(
                    lastLoginDate = todayIso,
                    lastBonusAt = System.currentTimeMillis(),
                    streak = bonus.streak,
                ),
            )
            profileDao.insertLoginDay(
                LoginDay(
                    date = todayIso,
                    bonusCoins = bonus.coins,
                    streakAtDay = bonus.streak,
                ),
            )
            ledger.earnInTransaction(
                amount = bonus.coins,
                type = CoinTransactionType.DAILY_BONUS,
                description = "Ежедневный бонус, день ${bonus.streak}",
            )

            DailyBonusResult.Claimed(
                coins = bonus.coins,
                streak = bonus.streak,
                isSevenDayMilestone = bonus.isSevenDayMilestone,
                newAchievements = achievements.evaluateInTransaction(),
            )
        }

    private fun ledgerFlowOrEmpty(): Flow<UserProfile> =
        profileDao.observeProfile().map { it ?: UserProfile() }
}
