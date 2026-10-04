package com.unfallenmore.quickjokes.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JokeRulesTest {

    @Test
    fun `valid joke passes`() {
        assertTrue(JokeRules.isValid("Анекдот!"))
        assertTrue(JokeRules.isValid("ab cd"))
    }

    @Test
    fun `too short joke is rejected`() {
        assertFalse(JokeRules.isValid(""))
        assertFalse(JokeRules.isValid("   "))
        assertFalse(JokeRules.isValid("ab"))
        assertFalse(JokeRules.isValid("  a  "))
    }

    @Test
    fun `surrounding spaces do not count`() {
        assertFalse(JokeRules.isValid("      ab      "))
        assertTrue(JokeRules.isValid("   abc   "))
    }

    @Test
    fun `normalize collapses inner spaces and trims`() {
        assertEquals("раз два", JokeRules.normalize("   раз     два   "))
        assertEquals("abc", JokeRules.normalize("abc"))
    }

    @Test
    fun `normalize keeps newlines untouched as single block`() {
        assertEquals("ab\ncd", JokeRules.normalize("ab\ncd"))
    }

    @Test
    fun `overlong joke is rejected`() {
        val tooLong = "a".repeat(JokeRules.MAX_LENGTH + 1)
        assertFalse(JokeRules.isValid(tooLong))
        assertTrue(JokeRules.isValid("a".repeat(JokeRules.MAX_LENGTH)))
    }

    @Test
    fun `audio joke is valid even with empty text`() {
        assertTrue(JokeRules.isValid("", hasAudio = true))
        assertTrue(JokeRules.isValid("   ", hasAudio = true))
        assertFalse(JokeRules.isValid("", hasAudio = false))
        assertFalse(JokeRules.isValid("ab", hasAudio = false))
        assertTrue(JokeRules.isValid("abc", hasAudio = false))
    }

    @Test
    fun `audio joke must be long enough to count`() {
        assertTrue(JokeRules.MIN_AUDIO_MS >= 500L)
    }
}
