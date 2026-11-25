package com.mefuturesaibot.app.ai

import org.junit.Assert.*
import org.junit.Test

class AiExchangeClockTest {

    @Test
    fun unavailableBeforeSynchronization() {
        val clock = AiExchangeClock()

        assertNull(clock.bounds(1000L))
    }

    @Test
    fun estimatesConservativeTimeBounds() {
        val clock = AiExchangeClock()

        assertTrue(
            clock.observe(
                100_000L,
                1000L,
                1200L
            )
        )

        val bounds = requireNotNull(
            clock.bounds(1500L)
        )

        assertEquals(100_300L, bounds.earliest)
        assertEquals(100_500L, bounds.latest)
    }

    @Test
    fun expiresOldSynchronization() {
        val clock = AiExchangeClock()

        clock.observe(
            100_000L,
            1000L,
            1200L
        )

        assertNotNull(clock.bounds(31_200L))
        assertNull(clock.bounds(31_201L))
    }

    @Test
    fun rejectsSlowNetworkResponse() {
        val clock = AiExchangeClock()

        assertFalse(
            clock.observe(
                100_000L,
                1000L,
                3001L
            )
        )

        assertNull(clock.bounds(3001L))
    }

    @Test
    fun rejectsInvalidServerTime() {
        val clock = AiExchangeClock()

        assertFalse(
            clock.observe(
                0L,
                1000L,
                1200L
            )
        )

        assertNull(clock.bounds(1200L))
    }

    @Test
    fun rejectsInvalidElapsedSequence() {
        val clock = AiExchangeClock()

        assertFalse(
            clock.observe(
                100_000L,
                2000L,
                1000L
            )
        )
    }

    @Test
    fun rejectsTimeBeforeAnchor() {
        val clock = AiExchangeClock()

        clock.observe(
            100_000L,
            1000L,
            1200L
        )

        assertNull(clock.bounds(1199L))
    }

    @Test
    fun clearRemovesSynchronization() {
        val clock = AiExchangeClock()

        clock.observe(
            100_000L,
            1000L,
            1200L
        )

        clock.clear()

        assertNull(clock.bounds(1200L))
    }

    @Test
    fun rejectsOutOfOrderObservation() {
        val clock = AiExchangeClock()

        assertTrue(
            clock.observe(
                200_000L,
                2000L,
                2200L
            )
        )

        assertFalse(
            clock.observe(
                100_000L,
                1000L,
                1200L
            )
        )

        assertEquals(
            200_000L,
            requireNotNull(
                clock.bounds(2200L)
            ).earliest
        )
    }
}
