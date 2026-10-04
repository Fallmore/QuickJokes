package com.unfallenmore.quickjokes.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.unfallenmore.quickjokes.data.local.entity.Joke
import com.unfallenmore.quickjokes.data.local.entity.Session
import com.unfallenmore.quickjokes.data.model.SessionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert
    suspend fun insertSession(session: Session): Long

    @Insert
    suspend fun insertJoke(joke: Joke): Long

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun observeSessions(): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeSession(id: Long): Flow<Session?>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSession(id: Long): Session?

    @Query("SELECT * FROM sessions WHERE topicId = :topicId ORDER BY startTime DESC")
    fun observeSessionsForTopic(topicId: Long): Flow<List<Session>>

    @Query("SELECT * FROM jokes WHERE sessionId = :sessionId ORDER BY orderIndex ASC")
    fun observeJokes(sessionId: Long): Flow<List<Joke>>

    @Query("SELECT * FROM jokes WHERE sessionId = :sessionId ORDER BY orderIndex ASC")
    suspend fun getJokes(sessionId: Long): List<Joke>

    @Query("SELECT * FROM jokes WHERE id = :id")
    suspend fun getJoke(id: Long): Joke?

    @Query("SELECT COUNT(*) FROM jokes WHERE sessionId = :sessionId")
    suspend fun countJokes(sessionId: Long): Int

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("DELETE FROM jokes WHERE id = :id")
    suspend fun deleteJoke(id: Long)

    /**
     * Финализация сессии: итоги + фактическое время и статус.
     * [endTime]/[status] обязательны — без них история показала бы
     * плановую длительность и «Время вышло» даже при досрочном выходе.
     */
    @Query(
        """
        UPDATE sessions
        SET totalJokes = :totalJokes, jokesPerMinute = :jpm, coinsEarned = :coins,
            endTime = :endTime, status = :status
        WHERE id = :id
        """,
    )
    suspend fun updateSessionStats(
        id: Long,
        totalJokes: Int,
        jpm: Double,
        coins: Int,
        endTime: Long,
        status: SessionStatus,
    )

    /**
     * Сколько сессий по теме завершены с шутками — только они считаются
     * прохождением темы (для бонуса «первое прохождение»).
     * Текущая сессия на момент вызова ещё не обновлена (totalJokes = 0),
     * поэтому считается, сколько прохождений было **до** неё.
     */
    @Query("SELECT COUNT(*) FROM sessions WHERE topicId = :topicId AND totalJokes > 0")
    suspend fun countCompletedSessionsForTopic(topicId: Long): Int
}
