package com.unfallenmore.quickjokes.data.backup

import com.unfallenmore.quickjokes.data.local.entity.CoinTransaction
import com.unfallenmore.quickjokes.data.local.entity.Joke
import com.unfallenmore.quickjokes.data.local.entity.LoginDay
import com.unfallenmore.quickjokes.data.local.entity.Session
import com.unfallenmore.quickjokes.data.local.entity.Topic
import com.unfallenmore.quickjokes.data.local.entity.UserAchievement
import com.unfallenmore.quickjokes.data.local.entity.UserProfile
import com.unfallenmore.quickjokes.data.model.CoinTransactionType
import com.unfallenmore.quickjokes.data.model.Difficulty
import com.unfallenmore.quickjokes.data.model.InputMode
import com.unfallenmore.quickjokes.data.model.SessionStatus
import com.unfallenmore.quickjokes.data.model.TopicType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {

    private fun sampleData() = BackupData(
        profile = UserProfile(
            coins = 106,
            lastBonusAt = 1_700_000_000_000,
            lastLoginDate = "2026-10-04",
            streak = 7,
            totalJokes = 42,
            customTopicSlots = 3,
            topicsUnlocked = 2,
            onboardingDone = true,
        ),
        loginDays = listOf(
            LoginDay(date = "2026-10-03", bonusCoins = 10, streakAtDay = 6),
            LoginDay(date = "2026-10-04", bonusCoins = 15, streakAtDay = 7),
        ),
        unlockedAchievements = listOf(
            UserAchievement(achievementCode = "first_joke", unlockedAt = 1_700_000_001_000),
        ),
        transactions = listOf(
            CoinTransaction(
                id = 9,
                amount = 19,
                type = CoinTransactionType.REWARD,
                description = "Сессия",
                timestamp = 1_700_000_002_000,
            ),
            CoinTransaction(
                id = 10,
                amount = -25,
                type = CoinTransactionType.CUSTOM_TOPIC_PURCHASE,
                description = "Слот темы",
                timestamp = 1_700_000_003_000,
            ),
        ),
        customTopics = listOf(
            Topic(
                id = 5,
                title = "Картинки-тест",
                description = "Подпись",
                type = TopicType.IMAGE,
                imagePath = "topic_images/a.img",
                situation = null,
                isCustom = true,
                isLocked = false,
                cost = 0,
                createdAt = 1_700_000_004_000,
            ),
            Topic(
                id = 6,
                title = "Ситуация-тест",
                description = "",
                type = TopicType.SITUATION,
                imagePath = null,
                situation = "Метро, час пик",
                isCustom = true,
                cost = 0,
                createdAt = 1_700_000_005_000,
            ),
        ),
        sessions = listOf(
            Session(
                id = 11,
                topicId = 5,
                startTime = 1_700_000_010_000,
                endTime = 1_700_000_070_000,
                durationSec = 60,
                difficulty = Difficulty.MEDIUM,
                inputMode = InputMode.BOTH,
                status = SessionStatus.FINISHED_EARLY,
                totalJokes = 3,
                jokesPerMinute = 3.0,
                coinsEarned = 7,
            ),
            Session(
                id = 12,
                topicId = -6,
                startTime = 1_700_000_100_000,
                endTime = 1_700_000_160_000,
                durationSec = 120,
                difficulty = Difficulty.EASY,
                inputMode = InputMode.VOICE,
                status = SessionStatus.TIME_OUT,
                totalJokes = 0,
                jokesPerMinute = 0.0,
                coinsEarned = 0,
            ),
        ),
        jokes = listOf(
            Joke(id = 21, sessionId = 11, text = "Первая шутка", createdAt = 1, orderIndex = 1),
            Joke(id = 22, sessionId = 11, text = "Вторая шутка", createdAt = 2, orderIndex = 2),
            // Голосовая шутка: текста нет, шутка — в записи
            Joke(
                id = 23,
                sessionId = 12,
                text = "",
                createdAt = 3,
                orderIndex = 1,
                audioPath = "joke_audio/voice.m4a",
            ),
        ),
        topicImages = mapOf("topic_images/a.img" to "aGVsbG8gd29ybGQ="),
        jokeAudios = mapOf("joke_audio/voice.m4a" to "dmlkZW9ieXRlcw=="),
    )

    @Test
    fun `encode decode keeps all data intact`() {
        val original = sampleData()
        val restored = BackupCodec.decode(BackupCodec.encode(original))
        assertEquals(original, restored)
    }

    @Test
    fun `profile id forced to single`() {
        val json = BackupCodec.encode(sampleData())
            // Профиль — первая запись с "id" в JSON; replaceFirst не трогает id сессий
            .replaceFirst("\"id\": 1", "\"id\": 77")
        val restored = BackupCodec.decode(json)
        assertEquals(UserProfile.SINGLE_ID, restored.profile.id)
    }

    @Test
    fun `not a json rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode("это не json")
        }
    }

    @Test
    fun `foreign json rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode("""{"format":"something-else","version":1}""")
        }
    }

    @Test
    fun `newer version rejected`() {
        val json = """{"format":"${BackupCodec.FORMAT}","version":999}"""
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode(json)
        }
    }

    @Test
    fun `minimal file decodes with defaults`() {
        val json = """{"format":"${BackupCodec.FORMAT}","version":${BackupCodec.VERSION}}"""
        val restored = BackupCodec.decode(json)
        assertEquals(UserProfile(), restored.profile.copy(id = UserProfile.SINGLE_ID))
        assertEquals(emptyList<Session>(), restored.sessions)
        assertEquals(emptyMap<String, String>(), restored.topicImages)
        assertEquals(emptyMap<String, String>(), restored.jokeAudios)
    }

    @Test
    fun `backup without audio fields (older export) decodes`() {
        // Файлы, сделанные до появления записей речи, не содержат
        // ни jokeAudios, ни audioPath у шуток — импорт должен работать
        val json = """
            {"format":"${BackupCodec.FORMAT}","version":${BackupCodec.VERSION},
             "jokes":[{"id":1,"sessionId":1,"text":"abc","createdAt":0,"orderIndex":1}]}
        """.trimIndent()
        val restored = BackupCodec.decode(json)
        val joke = restored.jokes.single()
        assertEquals(null, joke.audioPath)
        assertEquals(emptyMap<String, String>(), restored.jokeAudios)
    }

    @Test
    fun `unknown enum values degrade to defaults`() {
        val json = """
            {"format":"${BackupCodec.FORMAT}","version":${BackupCodec.VERSION},
             "sessions":[{"id":1,"difficulty":"IMPOSSIBLE","status":"WEIRD","inputMode":"TELEPATHY"}]}
        """.trimIndent()
        val restored = BackupCodec.decode(json)
        val session = restored.sessions.single()
        assertEquals(Difficulty.fromString(null), session.difficulty)
        assertEquals(SessionStatus.fromString(null), session.status)
        assertEquals(InputMode.fromString(null), session.inputMode)
    }
}
