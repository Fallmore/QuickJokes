package com.unfallenmore.quickjokes.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.unfallenmore.quickjokes.data.local.entity.CoinTransaction
import com.unfallenmore.quickjokes.data.local.entity.LoginDay
import com.unfallenmore.quickjokes.data.local.entity.UserAchievement
import com.unfallenmore.quickjokes.data.local.entity.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Операции с профилем, монетами, достижениями и входами.
 * Баланс и реестр транзакций всегда меняются вместе — см. CoinRepository.
 */
@Dao
interface ProfileDao {

    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observeProfile(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getProfile(): UserProfile?

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: UserProfile)

    @Insert
    suspend fun insertTransaction(tx: CoinTransaction): Long

    @Query("SELECT * FROM coin_transactions ORDER BY timestamp DESC")
    fun observeTransactions(): Flow<List<CoinTransaction>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM coin_transactions")
    suspend fun computeBalance(): Int

    // --- Достижения ---

    @Query("SELECT * FROM user_achievements")
    fun observeUnlocked(): Flow<List<UserAchievement>>

    /** Награды, полученные начиная с момента (для экрана результатов сессии). */
    @Query("SELECT * FROM user_achievements WHERE unlockedAt >= :since ORDER BY unlockedAt ASC")
    fun observeUnlockedSince(since: Long): Flow<List<UserAchievement>>

    @Query("SELECT achievementCode FROM user_achievements")
    suspend fun unlockedCodes(): List<String>

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun unlock(achievement: UserAchievement)

    // --- Входы / streak ---

    @Query("SELECT * FROM login_days ORDER BY date DESC LIMIT :limit")
    fun observeRecentLogins(limit: Int): Flow<List<LoginDay>>

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertLoginDay(day: LoginDay)
}
