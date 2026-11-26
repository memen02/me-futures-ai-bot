package com.mefuturesaibot.app.account

import org.junit.Assert.*
import org.junit.Test

/**
 * Deterministic balance scheduling policy tests.
 *
 * No network, Android clock, credentials,
 * account access or trading operations.
 */
class AccountBalancePolicyTest {

    private val policy = AccountBalancePolicy

    @Test
    fun displayRefreshIsOneSecond() {
        assertEquals(
            1000L,
            policy.DISPLAY_REFRESH_MS
        )
    }

    @Test
    fun normalRefreshIsTenSeconds() {
        assertEquals(
            10000L,
            policy.NORMAL_REFRESH_MS
        )
    }

    @Test
    fun minimumReadIntervalIsThreeSeconds() {
        assertEquals(
            3000L,
            policy.MIN_READ_INTERVAL_MS
        )
    }

    @Test
    fun eventDebounceIsFiveHundredMilliseconds() {
        assertEquals(
            500L,
            policy.EVENT_DEBOUNCE_MS
        )
    }

    @Test
    fun firstRetryUsesMinimumDelay() {
        assertEquals(
            3000L,
            policy.retryDelayMs(0, 0)
        )
    }

    @Test
    fun retryUsesExponentialBackoff() {
        val expected = listOf(
            3000L,
            6000L,
            12000L,
            24000L,
            48000L,
            60000L
        )

        expected.forEachIndexed { attempt, delay ->
            assertEquals(
                delay,
                policy.retryDelayMs(attempt, 0)
            )
        }
    }

    @Test
    fun retryAttemptIsClamped() {
        assertEquals(
            3000L,
            policy.retryDelayMs(Int.MIN_VALUE, 0)
        )

        assertEquals(
            60000L,
            policy.retryDelayMs(Int.MAX_VALUE, 0)
        )
    }

    @Test
    fun retryAppliesPositiveJitter() {
        assertEquals(
            3300L,
            policy.retryDelayMs(0, 10)
        )

        assertEquals(
            3900L,
            policy.retryDelayMs(0, 30)
        )
    }

    @Test
    fun negativeJitterIsClamped() {
        assertEquals(
            3000L,
            policy.retryDelayMs(0, Int.MIN_VALUE)
        )
    }

    @Test
    fun excessiveJitterIsClamped() {
        assertEquals(
            3900L,
            policy.retryDelayMs(0, Int.MAX_VALUE)
        )
    }

    @Test
    fun retryNeverExceedsMaximumDelay() {
        for (attempt in -5..20) {
            for (jitter in -10..50) {
                val delay =
                    policy.retryDelayMs(
                        attempt,
                        jitter
                    )

                assertTrue(
                    delay in 3000L..60000L
                )
            }
        }
    }

    @Test
    fun deadlineAddsDelay() {
        assertEquals(
            1500L,
            policy.deadline(1000L, 500L)
        )
    }

    @Test
    fun deadlineSaturatesOnOverflow() {
        assertEquals(
            Long.MAX_VALUE,
            policy.deadline(
                Long.MAX_VALUE - 10L,
                100L
            )
        )
    }

    @Test
    fun zeroDelayPreservesDeadline() {
        assertEquals(
            1000L,
            policy.deadline(1000L, 0L)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun deadlineRejectsNegativeClock() {
        policy.deadline(-1L, 1000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun deadlineRejectsNegativeDelay() {
        policy.deadline(1000L, -1L)
    }

    @Test
    fun remainingTimeBeforeDeadline() {
        assertEquals(
            500L,
            policy.remainingMs(1000L, 1500L)
        )
    }

    @Test
    fun remainingTimeAtAndAfterDeadline() {
        assertEquals(
            0L,
            policy.remainingMs(1500L, 1500L)
        )

        assertEquals(
            0L,
            policy.remainingMs(2000L, 1500L)
        )
    }

    @Test
    fun remainingTimeSupportsMaximumDeadline() {
        assertEquals(
            Long.MAX_VALUE,
            policy.remainingMs(
                0L,
                Long.MAX_VALUE
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun remainingTimeRejectsNegativeClock() {
        policy.remainingMs(-1L, 1000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun remainingTimeRejectsNegativeDeadline() {
        policy.remainingMs(1000L, -1L)
    }

    @Test
    fun firstReadHasNoIntervalDelay() {
        assertEquals(
            0L,
            policy.minimumReadDelayMs(
                -1L,
                0L
            )
        )
    }

    @Test
    fun minimumReadDelayHonorsExactBoundary() {
        assertEquals(
            3000L,
            policy.minimumReadDelayMs(
                1000L,
                1000L
            )
        )

        assertEquals(
            1L,
            policy.minimumReadDelayMs(
                1000L,
                3999L
            )
        )

        assertEquals(
            0L,
            policy.minimumReadDelayMs(
                1000L,
                4000L
            )
        )
    }

    @Test
    fun minimumReadDelayAfterBoundaryIsZero() {
        assertEquals(
            0L,
            policy.minimumReadDelayMs(
                1000L,
                10000L
            )
        )
    }

    @Test
    fun backwardClockDoesNotBypassInterval() {
        assertEquals(
            4000L,
            policy.minimumReadDelayMs(
                2000L,
                1000L
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun minimumReadDelayRejectsInvalidTimestamp() {
        policy.minimumReadDelayMs(-2L, 1000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun minimumReadDelayRejectsNegativeClock() {
        policy.minimumReadDelayMs(0L, -1L)
    }

    @Test
    fun nextReadUsesLargestDelay() {
        assertEquals(
            10000L,
            policy.nextReadDelayMs(
                10000L, 3000L, 0L, 0L
            )
        )

        assertEquals(
            3000L,
            policy.nextReadDelayMs(
                500L, 3000L, 0L, 0L
            )
        )

        assertEquals(
            12000L,
            policy.nextReadDelayMs(
                500L, 3000L, 12000L, 0L
            )
        )

        assertEquals(
            300000L,
            policy.nextReadDelayMs(
                500L, 3000L, 60000L, 300000L
            )
        )
    }

    @Test
    fun nextReadCanBeImmediatelyReady() {
        assertEquals(
            0L,
            policy.nextReadDelayMs(
                0L, 0L, 0L, 0L
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun nextReadRejectsNegativeRequestedDelay() {
        policy.nextReadDelayMs(-1L, 0L, 0L, 0L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nextReadRejectsNegativeIntervalDelay() {
        policy.nextReadDelayMs(0L, -1L, 0L, 0L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nextReadRejectsNegativeRetryDelay() {
        policy.nextReadDelayMs(0L, 0L, -1L, 0L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nextReadRejectsNegativeCooldown() {
        policy.nextReadDelayMs(0L, 0L, 0L, -1L)
    }

    @Test
    fun schedulerPreservesShortDelay() {
        assertEquals(
            500L,
            policy.scheduleDelayMs(500L)
        )

        assertEquals(
            0L,
            policy.scheduleDelayMs(0L)
        )
    }

    @Test
    fun schedulerCapsLongWakeUpDelay() {
        assertEquals(
            60000L,
            policy.scheduleDelayMs(60000L)
        )

        assertEquals(
            60000L,
            policy.scheduleDelayMs(300000L)
        )

        assertEquals(
            60000L,
            policy.scheduleDelayMs(Long.MAX_VALUE)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun schedulerRejectsNegativeDelay() {
        policy.scheduleDelayMs(-1L)
    }
}
