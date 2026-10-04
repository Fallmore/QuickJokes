package com.unfallenmore.quickjokes.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.unfallenmore.quickjokes.data.local.entity.Session

/**
 * Агрегаты для экранов статистики и проверки достижений.
 * Все даты — epoch millis; группировка по дню делается в зоне пользователя
 * (конвертация в SQL через DateTime — в [StatsQueries]).
 */
@Dao
interface StatsDao {

    /** Всего шуток за период [from, to] (включительно). */
    @Query("SELECT COALESCE(SUM(totalJokes), 0) FROM sessions WHERE startTime >= :from AND startTime < :to")
    suspend fun totalJokesBetween(from: Long, to: Long): Int

    /** Шуток за всё время. */
    @Query("SELECT COALESCE(SUM(totalJokes), 0) FROM sessions")
    suspend fun totalJokesAllTime(): Int

    @Query("SELECT COALESCE(SUM(coinsEarned), 0) FROM sessions WHERE startTime >= :from AND startTime < :to")
    suspend fun coinsBetween(from: Long, to: Long): Int

    @Query("SELECT COUNT(*) FROM sessions WHERE startTime >= :from AND startTime < :to")
    suspend fun sessionsBetween(from: Long, to: Long): Int

    /** Среднее шуток за сессию за период; 0, если сессий не было. */
    @Query(
        """
        SELECT CASE WHEN COUNT(*) = 0 THEN 0.0
                    ELSE CAST(SUM(totalJokes) AS REAL) / COUNT(*)
               END
        FROM sessions
        WHERE startTime >= :from AND startTime < :to
        """,
    )
    suspend fun avgJokesBetween(from: Long, to: Long): Double

    @Query("SELECT COALESCE(MAX(totalJokes), 0) FROM sessions WHERE startTime >= :from AND startTime < :to")
    suspend fun bestJokesBetween(from: Long, to: Long): Int

    /**
     * Лучший JPM за всё время (для достижений).
     * Сессии короче 15 сек не участвуют — как и бонусу за темп
     * ([RewardCalculator.JPM_MIN_ELAPSED_SEC]), ими нельзя разогнать темп.
     */
    @Query("SELECT COALESCE(MAX(jokesPerMinute), 0.0) FROM sessions WHERE (endTime - startTime) >= 15000")
    suspend fun bestJokesPerMinute(): Double

    /** Больше всего шуток за одну сессию (для достижений). */
    @Query("SELECT COALESCE(MAX(totalJokes), 0) FROM sessions")
    suspend fun bestJokesInSingleSession(): Int

    /** Тема считается пройденной, только если в сессии были шутки. */
    @Query("SELECT COUNT(DISTINCT topicId) FROM sessions WHERE totalJokes > 0")
    suspend fun distinctTopicsPlayed(): Int

    /** Разбивка по дням для графика: день (epoch millis начала суток в UTC) + сумма. */
    @Query(
        """
        SELECT (startTime / 86400000) * 86400000 AS bucket,
               SUM(totalJokes) AS value
        FROM sessions
        WHERE startTime >= :from AND startTime < :to
        GROUP BY bucket
        ORDER BY bucket ASC
        """,
    )
    suspend fun jokesPerDay(from: Long, to: Long): List<TimeBucket>

    /** Последние N сессий для мини-графика тренда. */
    @Query("SELECT * FROM sessions ORDER BY startTime DESC LIMIT :limit")
    suspend fun recentSessions(limit: Int): List<Session>
}

/** Результат группировки: bucket = epoch millis, value = агрегат. */
data class TimeBucket(
    val bucket: Long,
    val value: Double,
)
