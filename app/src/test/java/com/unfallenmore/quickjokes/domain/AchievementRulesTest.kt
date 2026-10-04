package com.unfallenmore.quickjokes.domain

import com.unfallenmore.quickjokes.data.model.AchievementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementRulesTest {

    private fun facts(
        totalJokes: Int = 0,
        bestJpm: Double = 0.0,
        bestJokesPerSession: Int = 0,
        streakDays: Int = 0,
        topicsPlayed: Int = 0,
        customTopicsCreated: Int = 0,
        topicsUnlocked: Int = 0,
    ) = AchievementFacts(
        totalJokes = totalJokes,
        bestJpm = bestJpm,
        bestJokesPerSession = bestJokesPerSession,
        streakDays = streakDays,
        topicsPlayed = topicsPlayed,
        customTopicsCreated = customTopicsCreated,
        topicsUnlocked = topicsUnlocked,
    )

    // --- isSatisfiedBy: границы порогов ---

    @Test
    fun `first joke unlocks on a single joke`() {
        assertTrue(AchievementType.FIRST_JOKE.isSatisfiedBy(facts(totalJokes = 1), 1))
        assertFalse(AchievementType.FIRST_JOKE.isSatisfiedBy(facts(totalJokes = 0), 1))
    }

    @Test
    fun `first joke ignores a large threshold`() {
        assertTrue(AchievementType.FIRST_JOKE.isSatisfiedBy(facts(totalJokes = 1), 100))
    }

    @Test
    fun `total jokes requires at least threshold`() {
        assertFalse(AchievementType.TOTAL_JOKES.isSatisfiedBy(facts(totalJokes = 9), 10))
        assertTrue(AchievementType.TOTAL_JOKES.isSatisfiedBy(facts(totalJokes = 10), 10))
        assertTrue(AchievementType.TOTAL_JOKES.isSatisfiedBy(facts(totalJokes = 11), 10))
    }

    @Test
    fun `jokes per session requires at least threshold`() {
        assertFalse(
            AchievementType.JOKES_PER_SESSION.isSatisfiedBy(facts(bestJokesPerSession = 14), 15),
        )
        assertTrue(
            AchievementType.JOKES_PER_SESSION.isSatisfiedBy(facts(bestJokesPerSession = 15), 15),
        )
    }

    @Test
    fun `jokes per minute compares doubles`() {
        assertFalse(AchievementType.JOKES_PER_MINUTE.isSatisfiedBy(facts(bestJpm = 4.9), 5))
        assertTrue(AchievementType.JOKES_PER_MINUTE.isSatisfiedBy(facts(bestJpm = 5.0), 5))
        assertTrue(AchievementType.JOKES_PER_MINUTE.isSatisfiedBy(facts(bestJpm = 12.75), 10))
    }

    @Test
    fun `streak days requires at least threshold`() {
        assertFalse(AchievementType.STREAK_DAYS.isSatisfiedBy(facts(streakDays = 6), 7))
        assertTrue(AchievementType.STREAK_DAYS.isSatisfiedBy(facts(streakDays = 7), 7))
    }

    @Test
    fun `topics played requires at least threshold`() {
        assertFalse(AchievementType.TOPICS_PLAYED.isSatisfiedBy(facts(topicsPlayed = 2), 3))
        assertTrue(AchievementType.TOPICS_PLAYED.isSatisfiedBy(facts(topicsPlayed = 3), 3))
    }

    @Test
    fun `custom topics requires at least threshold`() {
        assertFalse(
            AchievementType.CUSTOM_TOPICS_CREATED.isSatisfiedBy(facts(customTopicsCreated = 0), 1),
        )
        assertTrue(
            AchievementType.CUSTOM_TOPICS_CREATED.isSatisfiedBy(facts(customTopicsCreated = 1), 1),
        )
    }

    @Test
    fun `unlocked topics requires at least threshold`() {
        assertFalse(AchievementType.TOPICS_UNLOCKED.isSatisfiedBy(facts(topicsUnlocked = 17), 18))
        assertTrue(AchievementType.TOPICS_UNLOCKED.isSatisfiedBy(facts(topicsUnlocked = 18), 18))
    }

    // --- progressFractionOf ---

    @Test
    fun `progress is zero for empty facts`() {
        for (type in AchievementType.entries) {
            assertEquals(0f, type.progressFractionOf(AchievementFacts.EMPTY, 10), 0.0001f)
        }
    }

    @Test
    fun `progress is clamped to one`() {
        val fraction = AchievementType.TOTAL_JOKES.progressFractionOf(facts(totalJokes = 500), 10)
        assertEquals(1f, fraction, 0.0001f)
    }

    @Test
    fun `progress is a share of the threshold`() {
        val half = AchievementType.STREAK_DAYS.progressFractionOf(facts(streakDays = 3), 6)
        assertEquals(0.5f, half, 0.0001f)

        val third = AchievementType.TOTAL_JOKES.progressFractionOf(facts(totalJokes = 5), 15)
        assertEquals(5f / 15f, third, 0.0001f)
    }

    @Test
    fun `progress is zero when threshold is not positive`() {
        assertEquals(
            0f,
            AchievementType.TOTAL_JOKES.progressFractionOf(facts(totalJokes = 42), 0),
            0.0001f,
        )
        assertEquals(
            0f,
            AchievementType.TOTAL_JOKES.progressFractionOf(facts(totalJokes = 42), -5),
            0.0001f,
        )
    }

    @Test
    fun `progress uses fractional facts for jpm`() {
        val fraction = AchievementType.JOKES_PER_MINUTE.progressFractionOf(facts(bestJpm = 2.5), 5)
        assertEquals(0.5f, fraction, 0.0001f)
    }

    @Test
    fun `satisfied achievement always shows full progress`() {
        for (type in AchievementType.entries) {
            val f = type.progressFractionOf(facts(totalJokes = 20, bestJpm = 20.0, bestJokesPerSession = 20, streakDays = 20, topicsPlayed = 20, customTopicsCreated = 20, topicsUnlocked = 20), 10)
            assertEquals("type=$type", 1f, f, 0.0001f)
            assertTrue("type=$type", type.isSatisfiedBy(facts(totalJokes = 20, bestJpm = 20.0, bestJokesPerSession = 20, streakDays = 20, topicsPlayed = 20, customTopicsCreated = 20, topicsUnlocked = 20), 10))
        }
    }
}
