package com.unfallenmore.quickjokes.data.repository

import androidx.room.withTransaction
import com.unfallenmore.quickjokes.data.local.QuickJokesDatabase
import com.unfallenmore.quickjokes.data.local.dao.ProfileDao
import com.unfallenmore.quickjokes.data.local.entity.CoinTransaction
import com.unfallenmore.quickjokes.data.local.entity.UserProfile
import com.unfallenmore.quickjokes.data.model.CoinTransactionType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Единая точка изменения баланса монет.
 *
 * Инвариант: запись в реестр [CoinTransaction] и обновление кэша
 * [UserProfile.coins] происходят только вместе, иначе баланс и реестр
 * разойтись при падении процесса.
 *
 * Публичные методы открывают собственную транзакцию; методы с суффиксом
 * `InTransaction` рассчитаны на уже открытую транзакцию ([inTransaction]),
 * чтобы начисления в рамках одного доменного события были одним атомом
 * и не вкладывались друг в друга.
 */
@Singleton
class CoinLedger @Inject constructor(
    private val db: QuickJokesDatabase,
    private val profileDao: ProfileDao,
) {
    /** Выполнить блок атомарно (внутри одной транзакции БД). */
    suspend fun <T> inTransaction(block: suspend () -> T): T = db.withTransaction(block)

    suspend fun earn(
        amount: Int,
        type: CoinTransactionType,
        description: String,
        timestamp: Long = System.currentTimeMillis(),
    ): Boolean = db.withTransaction {
        earnInTransaction(amount, type, description, timestamp)
    }

    suspend fun spend(
        amount: Int,
        type: CoinTransactionType,
        description: String,
        timestamp: Long = System.currentTimeMillis(),
    ): Boolean = db.withTransaction {
        spendInTransaction(amount, type, description, timestamp)
    }

    /**
     * Начислить монеты в открытой транзакции. false — amount <= 0.
     */
    suspend fun earnInTransaction(
        amount: Int,
        type: CoinTransactionType,
        description: String,
        timestamp: Long = System.currentTimeMillis(),
    ): Boolean {
        if (amount <= 0) return false
        val profile = getOrCreateProfile()
        profileDao.insertTransaction(
            CoinTransaction(
                amount = amount,
                type = type,
                description = description,
                timestamp = timestamp,
            ),
        )
        profileDao.upsertProfile(profile.copy(coins = profile.coins + amount))
        return true
    }

    /**
     * Списать монеты в открытой транзакции. false — недостаточно средств
     * (ничего не записывается).
     */
    suspend fun spendInTransaction(
        amount: Int,
        type: CoinTransactionType,
        description: String,
        timestamp: Long = System.currentTimeMillis(),
    ): Boolean {
        if (amount <= 0) return false
        val profile = getOrCreateProfile()
        if (profile.coins < amount) return false

        profileDao.insertTransaction(
            CoinTransaction(
                amount = -amount,
                type = type,
                description = description,
                timestamp = timestamp,
            ),
        )
        profileDao.upsertProfile(profile.copy(coins = profile.coins - amount))
        return true
    }

    /** Баланс из кэша профиля; при первом обращении создаёт строку профиля. */
    suspend fun balance(): Int = getOrCreateProfile().coins

    /**
     * Обновить профиль в открытой транзакции (счётчики, streak и т.п.),
     * не затрагивая баланс.
     */
    suspend fun updateProfileInTransaction(
        transform: (UserProfile) -> UserProfile,
    ): UserProfile {
        val current = getOrCreateProfile()
        val updated = transform(current)
        profileDao.upsertProfile(updated)
        return updated
    }

    suspend fun getOrCreateProfile(): UserProfile {
        profileDao.getProfile()?.let { return it }
        val created = UserProfile()
        profileDao.upsertProfile(created)
        return created
    }

    /** Пересчитать кэш баланса из реестра (диагностика/восстановление). */
    suspend fun recomputeBalanceFromLedger() {
        db.withTransaction {
            val profile = getOrCreateProfile()
            val actual = profileDao.computeBalance()
            if (actual != profile.coins) {
                profileDao.upsertProfile(profile.copy(coins = actual))
            }
        }
    }
}
