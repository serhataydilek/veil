package com.veil.app.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConservativeTimeTest {
    @Test
    fun wallRollbackOnSameBootDoesNotDecreaseLowerBound() {
        val clock = FakeRetentionClock(wallMs = 10_000, elapsedMs = 1_000)
        val previous = ConservativeTime.initialize(clock)
        clock.wallMs = 1_000
        clock.elapsedMs = 1_500
        val advanced = ConservativeTime.advance(previous, clock)
        assertFalse(advanced.expireAllMessages)
        assertEquals(10_500L, advanced.bound.wallLowerBoundMs)
    }

    @Test
    fun wallJumpForwardCanAdvanceLowerBound() {
        val clock = FakeRetentionClock(wallMs = 10_000, elapsedMs = 0)
        val previous = ConservativeTime.initialize(clock)
        clock.wallMs = 40_000
        clock.elapsedMs = 100
        val advanced = ConservativeTime.advance(previous, clock)
        assertFalse(advanced.expireAllMessages)
        assertEquals(40_000L, advanced.bound.wallLowerBoundMs)
    }

    @Test
    fun elapsedTimeAdvancesWhileWallIsFrozen() {
        val clock = FakeRetentionClock(wallMs = 10_000, elapsedMs = 0)
        val previous = ConservativeTime.initialize(clock)
        clock.elapsedMs = 7_000
        val advanced = ConservativeTime.advance(previous, clock)
        assertFalse(advanced.expireAllMessages)
        assertEquals(17_000L, advanced.bound.wallLowerBoundMs)
    }

    @Test
    fun bootChangeExpiresMessagesEarly() {
        val clock = FakeRetentionClock(wallMs = 10_000, elapsedMs = 5_000)
        val previous = ConservativeTime.initialize(clock)
        clock.boot = BootObservation(bootCount = 2, reliable = true)
        clock.elapsedMs = 10
        val advanced = ConservativeTime.advance(previous, clock)
        assertTrue(advanced.expireAllMessages)
        assertEquals(10_000L, advanced.bound.wallLowerBoundMs)
    }

    @Test
    fun ambiguousBootExpiresMessagesEarly() {
        val clock = FakeRetentionClock(
            wallMs = 10_000,
            elapsedMs = 5_000,
            boot = BootObservation(bootCount = 1, reliable = true),
        )
        val previous = ConservativeTime.initialize(clock)
        clock.boot = BootObservation(bootCount = null, reliable = false)
        val advanced = ConservativeTime.advance(previous, clock)
        assertTrue(advanced.expireAllMessages)
    }
}
