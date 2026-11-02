package com.abcamiletto.wortschatz.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionQueueTest {
    @Test fun passedItemsLeaveTheQueue() {
        val queue = SessionQueue(listOf("a", "b"))
        queue.pass()
        assertEquals("b", queue.current)
        assertEquals(1, queue.completed)
        queue.pass()
        assertNull(queue.current)
        assertEquals(2, queue.completed)
    }

    @Test fun failedItemReturnsAfterTheGap() {
        val queue = SessionQueue(listOf("a", "b", "c", "d", "e"), retryGap = 3)
        queue.retryLater()
        assertEquals(listOf("b", "c", "d", "a"), drain(queue, 4))
    }

    @Test fun lastItemKeepsReturningUntilPassed() {
        val queue = SessionQueue(listOf("a"))
        queue.retryLater()
        assertEquals("a", queue.current)
        assertEquals(0, queue.completed)
        queue.pass()
        assertNull(queue.current)
    }

    private fun drain(queue: SessionQueue<String>, count: Int) = List(count) {
        queue.current!!.also { queue.pass() }
    }
}
