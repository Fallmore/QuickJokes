package com.unfallenmore.quickjokes.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicIdsTest {

    @Test
    fun `first custom topic starts from one on an empty table`() {
        assertEquals(1L, TopicIds.nextCustomId(null))
    }

    @Test
    fun `preset topics with negative ids do not affect the first custom id`() {
        assertEquals(1L, TopicIds.nextCustomId(-1L))
        assertEquals(1L, TopicIds.nextCustomId(-16L))
    }

    @Test
    fun `legacy row with id zero does not collide`() {
        assertEquals(1L, TopicIds.nextCustomId(0L))
    }

    @Test
    fun `next id is max plus one`() {
        assertEquals(2L, TopicIds.nextCustomId(1L))
        assertEquals(7L, TopicIds.nextCustomId(6L))
        assertEquals(101L, TopicIds.nextCustomId(100L))
    }

    @Test
    fun `ids are always positive and strictly increasing`() {
        var max: Long? = null
        val ids = buildList {
            repeat(30) {
                val id = TopicIds.nextCustomId(max)
                add(id)
                max = id
            }
        }
        assertEquals(30, ids.size)
        assertEquals(30L, ids.distinct().size.toLong())
        assertEquals(ids, ids.sorted())
        // Предустановленные id отрицательные — пересечься с ними не могут
        assertTrue(ids.all { it > 0L })
    }
}
