package com.mefuturesaibot.app.ai

import org.junit.Assert.*
import org.junit.Test

class AiRateLimitGuardTest {

    @Test
    fun permitsFirstRequest() {
        val guard = AiRateLimitGuard()

        assertTrue(guard.acquire(0L, 2))
        assertEquals(2, guard.usedWeight(0L))

        guard.release()
    }

    @Test
    fun blocksConcurrentRequest() {
        val guard = AiRateLimitGuard()

        assertTrue(guard.acquire(0L, 2))
        assertFalse(guard.acquire(1000L, 2))

        guard.release()

        assertTrue(guard.acquire(1000L, 2))
        guard.release()
    }

    @Test
    fun enforcesMinimumSpacing() {
        val guard = AiRateLimitGuard()

        assertTrue(guard.acquire(0L, 2))
        guard.release()

        assertFalse(guard.acquire(499L, 2))
        assertTrue(guard.acquire(500L, 2))

        guard.release()
    }

    @Test
    fun enforcesWeightBudget() {
        val guard = AiRateLimitGuard(
            weightBudget = 4
        )

        assertTrue(guard.acquire(0L, 2))
        guard.release()

        assertTrue(guard.acquire(500L, 2))
        guard.release()

        assertFalse(guard.acquire(1000L, 2))
        assertEquals(4, guard.usedWeight(1000L))
    }

    @Test
    fun restoresBudgetAfterOneMinute() {
        val guard = AiRateLimitGuard(
            weightBudget = 2
        )

        assertTrue(guard.acquire(0L, 2))
        guard.release()

        assertFalse(guard.acquire(59_999L, 2))
        assertTrue(guard.acquire(60_000L, 2))

        guard.release()
    }

    @Test
    fun http429TriggersCooldown() {
        val guard = AiRateLimitGuard()

        guard.onHttpResponse(
            429, null, 1000L
        )

        assertEquals(
            60_000L,
            guard.cooldownRemaining(1000L)
        )

        assertFalse(guard.acquire(60_999L, 2))
        assertTrue(guard.acquire(61_000L, 2))

        guard.release()
    }

    @Test
    fun respectsLongerRetryAfter() {
        val guard = AiRateLimitGuard()

        guard.onHttpResponse(
            429, 120L, 1000L
        )

        assertEquals(
            120_000L,
            guard.cooldownRemaining(1000L)
        )

        assertFalse(guard.acquire(120_999L, 2))
        assertTrue(guard.acquire(121_000L, 2))

        guard.release()
    }

    @Test
    fun http418TriggersLongCooldown() {
        val guard = AiRateLimitGuard()

        guard.onHttpResponse(
            418, null, 0L
        )

        assertEquals(
            3_600_000L,
            guard.cooldownRemaining(0L)
        )

        assertFalse(
            guard.acquire(3_599_999L, 2)
        )

        assertTrue(
            guard.acquire(3_600_000L, 2)
        )

        guard.release()
    }

    @Test
    fun shorterCooldownCannotOverrideLongerOne() {
        val guard = AiRateLimitGuard()

        guard.onHttpResponse(
            418, 7200L, 0L
        )

        guard.onHttpResponse(
            429, 60L, 1000L
        )

        assertEquals(
            7_199_000L,
            guard.cooldownRemaining(1000L)
        )
    }

    @Test
    fun successfulResponseDoesNotResetCooldown() {
        val guard = AiRateLimitGuard()

        guard.onHttpResponse(
            429, 60L, 0L
        )

        guard.onHttpResponse(
            200, null, 1000L
        )

        assertEquals(
            59_000L,
            guard.cooldownRemaining(1000L)
        )
    }
}
