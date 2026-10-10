package io.github.mothlight

import kotlin.test.*

class ProbeFeedTest {
    @Test fun disabledFeedDropsEventsAndResumesWithoutReplay() {
        var enabled = false
        val feed = ProbeFeed<Int>(2) { enabled }
        feed.record(1)
        assertEquals(emptyList(), feed.items.value)
        enabled = true
        feed.record(2)
        enabled = false
        feed.record(3)
        assertEquals(listOf(2), feed.items.value)
    }
    @Test fun overflowEvictsOldestAndClearAllowsNewEvents() {
        val feed = ProbeFeed<Int>(2)
        listOf(1, 2, 3).forEach(feed::record)
        assertEquals(listOf(2, 3), feed.items.value)
        feed.clear()
        assertEquals(emptyList(), feed.items.value)
        feed.record(4)
        assertEquals(listOf(4), feed.items.value)
    }
    @Test fun instancesDoNotShareEvents() {
        val first = ProbeFeed<Int>(1)
        val second = ProbeFeed<Int>(1)
        first.record(9)
        assertEquals(emptyList(), second.items.value)
        second.record(7)
        first.clear()
        assertEquals(listOf(7), second.items.value)
    }
    @Test fun invalidCapacityIsRejectedAtConstruction() {
        assertFailsWith<IllegalArgumentException> { ProbeFeed<Int>(0) }
        assertFailsWith<IllegalArgumentException> { ProbeFeed<Int>(-1) }
    }
}
