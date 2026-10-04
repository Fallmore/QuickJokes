package com.unfallenmore.quickjokes.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.unfallenmore.quickjokes.data.local.entity.CoinTransaction
import com.unfallenmore.quickjokes.data.local.entity.Joke
import com.unfallenmore.quickjokes.data.local.entity.LoginDay
import com.unfallenmore.quickjokes.data.local.entity.Session
import com.unfallenmore.quickjokes.data.local.entity.Topic
import com.unfallenmore.quickjokes.data.local.entity.UserAchievement
import com.unfallenmore.quickjokes.data.local.entity.UserProfile

/**
 * Выгрузка и заливка всех пользовательских данных для экспорта/импорта.
 *
 * Отдельный DAO, чтобы не раздувать игровые DAO методами бэкапа.
 * Порядок операций при импорте (в одной транзакции):
 * очистка (сессии → транзакции → достижения → входы → свои темы),
 * затем вставка в порядке FK: темы → сессии → шутки → остальное.
 *
 * Каталог достижений ([com.unfallenmore.quickjokes.data.local.entity.Achievement])
 * не выгруживается — он сидится из кода и одинаков на всех устройствах.
 */
@Dao
interface BackupDao {

    // --- Чтение ---

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun profile(): UserProfile?

    @Query("SELECT * FROM login_days")
    suspend fun loginDays(): List<LoginDay>

    @Query("SELECT * FROM user_achievements")
    suspend fun unlockedAchievements(): List<UserAchievement>

    @Query("SELECT * FROM coin_transactions")
    suspend fun transactions(): List<CoinTransaction>

    @Query("SELECT * FROM topics WHERE isCustom = 1")
    suspend fun customTopics(): List<Topic>

    @Query("SELECT * FROM sessions")
    suspend fun sessions(): List<Session>

    @Query("SELECT * FROM jokes")
    suspend fun jokes(): List<Joke>

    // --- Очистка (порядок важен из-за внешних ключей) ---

    /** Шутки уходят каскадом вместе с сессиями. */
    @Query("DELETE FROM sessions")
    suspend fun clearSessions()

    @Query("DELETE FROM coin_transactions")
    suspend fun clearTransactions()

    @Query("DELETE FROM user_achievements")
    suspend fun clearUnlockedAchievements()

    @Query("DELETE FROM login_days")
    suspend fun clearLoginDays()

    @Query("DELETE FROM topics WHERE isCustom = 1")
    suspend fun clearCustomTopics()

    // --- Запись (id сохраняются: шутки ссылаются на сессии, сессии — на темы) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoginDays(days: List<LoginDay>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnlockedAchievements(items: List<UserAchievement>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(items: List<CoinTransaction>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopics(items: List<Topic>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(items: List<Session>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJokes(items: List<Joke>)
}
