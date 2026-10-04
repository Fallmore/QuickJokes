package com.unfallenmore.quickjokes.data.repository

import com.unfallenmore.quickjokes.data.local.dao.SessionDao
import com.unfallenmore.quickjokes.data.local.dao.ProfileDao
import com.unfallenmore.quickjokes.data.local.entity.Achievement
import com.unfallenmore.quickjokes.data.local.entity.Joke
import com.unfallenmore.quickjokes.data.local.entity.Session
import com.unfallenmore.quickjokes.data.model.CoinTransactionType
import com.unfallenmore.quickjokes.data.model.Difficulty
import com.unfallenmore.quickjokes.data.model.InputMode
import com.unfallenmore.quickjokes.data.model.SessionStatus
import com.unfallenmore.quickjokes.domain.JokeRules
import com.unfallenmore.quickjokes.domain.RewardCalculator
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Итог завершённой сессии — отдаётся на экран результатов. */
data class SessionResult(
    val session: Session,
    val jokes: List<Joke>,
    val coinsEarned: Int,
    val firstTopicCompletion: Boolean,
    val newAchievements: List<Achievement>,
)

/**
 * Игровая сессия: создание, добавление шуток, завершение с пересчётом наград.
 *
 * Шутки пишутся в БД по мере ввода (переживают сворачивание и убийство
 * процесса), поэтому таймер может спокойно считать по `endTimestamp`.
 */
@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val profileDao: ProfileDao,
    private val ledger: CoinLedger,
    private val achievements: AchievementEvaluator,
    private val audioStore: JokeAudioStore,
) {
    fun observeSession(id: Long): Flow<Session?> = sessionDao.observeSession(id)

    fun observeJokes(sessionId: Long): Flow<List<Joke>> = sessionDao.observeJokes(sessionId)

    fun observeHistory(): Flow<List<Session>> = sessionDao.observeSessions()

    suspend fun startSession(
        topicId: Long,
        durationSec: Int,
        difficulty: Difficulty,
        inputMode: InputMode,
        startMillis: Long = System.currentTimeMillis(),
    ): Long {
        val session = Session(
            topicId = topicId,
            startTime = startMillis,
            endTime = startMillis + durationSec * 1000L,
            durationSec = durationSec,
            difficulty = difficulty,
            inputMode = inputMode,
            status = SessionStatus.TIME_OUT,
            totalJokes = 0,
        )
        return sessionDao.insertSession(session)
    }

    /**
     * Добавить шутку. false — текст не прошёл правила ([JokeRules]).
     */
    suspend fun addJoke(sessionId: Long, rawText: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val text = JokeRules.normalize(rawText)
        if (!JokeRules.isValid(text)) return false

        val order = sessionDao.countJokes(sessionId) + 1
        sessionDao.insertJoke(
            Joke(
                sessionId = sessionId,
                text = text,
                createdAt = nowMillis,
                orderIndex = order,
            ),
        )
        return true
    }

    /**
     * Добавить голосовую шутку (диктофон): текст пуст, шутка — в записи.
     * [audioRelativePath] — путь из [JokeAudioStore] к уже записанному файлу.
     */
    suspend fun addAudioJoke(
        sessionId: Long,
        audioRelativePath: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        if (audioRelativePath.isBlank()) return false

        val order = sessionDao.countJokes(sessionId) + 1
        sessionDao.insertJoke(
            Joke(
                sessionId = sessionId,
                text = "",
                createdAt = nowMillis,
                orderIndex = order,
                audioPath = audioRelativePath,
            ),
        )
        return true
    }

    /**
     * Завершить сессию: пересчитать фактическую длительность, награду,
     * первую победу над темой, общие счётчики и достижения — одним атомом.
     */
    suspend fun finishSession(
        sessionId: Long,
        status: SessionStatus,
        nowMillis: Long = System.currentTimeMillis(),
    ): SessionResult? {
        val session = sessionDao.getSession(sessionId) ?: return null

        return ledger.inTransaction {
            val jokes = sessionDao.getJokes(sessionId)
            val elapsed = RewardCalculator.elapsedSeconds(session.startTime, nowMillis)
            val jpm = RewardCalculator.roundedJpm(jokes.size, elapsed)
            val baseReward = RewardCalculator.rewardCoins(
                difficulty = session.difficulty,
                jokesCount = jokes.size,
                elapsedSec = elapsed,
                plannedSec = session.durationSec,
            )

            // Прохождение темы — только если в сессии есть шутки и до неё
            // не было ни одного прохождения: пустые забросы не дают ни базу,
            // ни бонус за первое прохождение.
            val firstCompletion = jokes.isNotEmpty() &&
                sessionDao.countCompletedSessionsForTopic(session.topicId) == 0
            val firstBonus = if (firstCompletion) FIRST_COMPLETION_BONUS else 0
            val coins = baseReward + firstBonus

            sessionDao.updateSessionStats(
                id = session.id,
                totalJokes = jokes.size,
                jpm = jpm,
                coins = coins,
                endTime = nowMillis,
                status = status,
            )

            val profile = ledger.getOrCreateProfile()
            profileDao.upsertProfile(
                profile.copy(totalJokes = profile.totalJokes + jokes.size),
            )

            ledger.earnInTransaction(
                amount = coins,
                type = CoinTransactionType.REWARD,
                description = "Награда за сессию",
            )

            val newAchievements = achievements.evaluateInTransaction()

            val updated = session.copy(
                endTime = nowMillis,
                status = status,
                totalJokes = jokes.size,
                jokesPerMinute = jpm,
                coinsEarned = coins,
            )

            SessionResult(
                session = updated,
                jokes = jokes,
                coinsEarned = coins,
                firstTopicCompletion = firstCompletion,
                newAchievements = newAchievements,
            )
        }
    }

    suspend fun deleteSession(sessionId: Long) {
        // Шутки уйдут каскадом — сначала сохраняем пути их записей
        val audioPaths = sessionDao.getJokes(sessionId).mapNotNull { it.audioPath }
        sessionDao.deleteSession(sessionId)
        audioPaths.forEach { audioStore.delete(it) }
    }

    suspend fun deleteJoke(jokeId: Long) {
        val audioPath = sessionDao.getJoke(jokeId)?.audioPath
        sessionDao.deleteJoke(jokeId)
        audioStore.delete(audioPath)
    }

    suspend fun getJokes(sessionId: Long): List<Joke> = sessionDao.getJokes(sessionId)

    companion object {
        /** Бонус за первое прохождение темы. */
        const val FIRST_COMPLETION_BONUS = 15
    }
}
