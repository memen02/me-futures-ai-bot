package com.mefuturesaibot.app.account

import org.junit.Assert.*
import org.junit.Test

class AccountSessionPolicyTest {

    private val policy = AccountSessionPolicy

    @Test
    fun keepaliveUsesThirtyMinutes() {
        assertEquals(
            30L * 60L * 1000L,
            policy.KEEPALIVE_MS
        )
    }

    @Test
    fun keyLifetimeUsesFiftyFiveMinutes() {
        assertEquals(
            55L * 60L * 1000L,
            policy.KEY_MAX_AGE_MS
        )

        assertTrue(
            policy.KEEPALIVE_MS <
                policy.KEY_MAX_AGE_MS
        )
    }

    @Test
    fun socketLifetimeUsesTwentyThreeHours() {
        assertEquals(
            23L * 60L * 60L * 1000L,
            policy.SOCKET_MAX_AGE_MS
        )
    }

    @Test
    fun connectionTimeoutUsesFifteenSeconds() {
        assertEquals(
            15000L,
            policy.CONNECT_TIMEOUT_MS
        )
    }

    @Test
    fun reconnectStartsAtTwoSeconds() {
        assertEquals(
            2000L,
            policy.reconnectDelayMs(0)
        )
    }

    @Test
    fun reconnectBackoffGrowsExponentially() {
        val expected = listOf(
            2000L,
            4000L,
            8000L,
            16000L,
            32000L,
            60000L
        )

        val actual = (0..5).map {
            policy.reconnectDelayMs(it)
        }

        assertEquals(expected, actual)
    }

    @Test
    fun negativeAttemptUsesMinimumBackoff() {
        assertEquals(
            2000L,
            policy.reconnectDelayMs(-1)
        )

        assertEquals(
            2000L,
            policy.reconnectDelayMs(Int.MIN_VALUE)
        )
    }

    @Test
    fun extremeAttemptCannotOverflow() {
        assertEquals(
            60000L,
            policy.reconnectDelayMs(Int.MAX_VALUE)
        )
    }

    @Test
    fun reconnectJitterUsesRequestedPercentage() {
        assertEquals(
            2300L,
            policy.reconnectDelayMs(0, 15)
        )

        assertEquals(
            10400L,
            policy.reconnectDelayMs(2, 30)
        )
    }

    @Test
    fun negativeJitterIsIgnored() {
        assertEquals(
            2000L,
            policy.reconnectDelayMs(
                0,
                Int.MIN_VALUE
            )
        )
    }

    @Test
    fun excessiveJitterIsBounded() {
        assertEquals(
            2600L,
            policy.reconnectDelayMs(
                0,
                Int.MAX_VALUE
            )
        )
    }

    @Test
    fun jitterCannotExceedMaximumBackoff() {
        for (attempt in 0..100) {
            for (jitter in 0..30) {
                val delay =
                    policy.reconnectDelayMs(
                        attempt,
                        jitter
                    )

                assertTrue(
                    delay in 2000L..60000L
                )
            }
        }
    }

    @Test
    fun retryUsesLongerCooldown() {
        assertEquals(
            600000L,
            policy.retryDelayMs(
                0,
                600000L
            )
        )
    }

    @Test
    fun retryUsesBackoffWhenCooldownIsShorter() {
        assertEquals(
            8000L,
            policy.retryDelayMs(
                2,
                1000L
            )
        )
    }

    @Test
    fun negativeCooldownDoesNotReduceBackoff() {
        assertEquals(
            4000L,
            policy.retryDelayMs(
                1,
                Long.MIN_VALUE
            )
        )
    }

    @Test
    fun maximumCooldownIsPreserved() {
        assertEquals(
            Long.MAX_VALUE,
            policy.retryDelayMs(
                Int.MAX_VALUE,
                Long.MAX_VALUE,
                30
            )
        )
    }

    @Test
    fun shortScheduleDelayIsPreserved() {
        assertEquals(
            1500L,
            policy.scheduleDelayMs(1500L)
        )
    }

    @Test
    fun longScheduleDelayIsBounded() {
        assertEquals(
            60000L,
            policy.scheduleDelayMs(600000L)
        )

        assertEquals(
            60000L,
            policy.scheduleDelayMs(Long.MAX_VALUE)
        )
    }

    @Test
    fun negativeScheduleDelayBecomesZero() {
        assertEquals(
            0L,
            policy.scheduleDelayMs(Long.MIN_VALUE)
        )

        assertEquals(
            0L,
            policy.scheduleDelayMs(0L)
        )
    }

    @Test
    fun newlyRenewedKeyIsUsable() {
        assertTrue(
            policy.isKeyUsable(
                1000L,
                1000L
            )
        )
    }

    @Test
    fun keyIsUsableBeforeExactExpiry() {
        val start = 1000L

        assertTrue(
            policy.isKeyUsable(
                start,
                start + policy.KEY_MAX_AGE_MS - 1L
            )
        )
    }

    @Test
    fun keyExpiresAtExactBoundary() {
        val start = 1000L

        assertFalse(
            policy.isKeyUsable(
                start,
                start + policy.KEY_MAX_AGE_MS
            )
        )

        assertFalse(
            policy.isKeyUsable(
                start,
                Long.MAX_VALUE
            )
        )
    }

    @Test
    fun invalidKeyTimestampsAreRejected() {
        assertFalse(
            policy.isKeyUsable(-1L, 1000L)
        )

        assertFalse(
            policy.isKeyUsable(1000L, 999L)
        )

        assertFalse(
            policy.isKeyUsable(
                Long.MIN_VALUE,
                Long.MAX_VALUE
            )
        )
    }

    @Test
    fun socketExpiresAtExactBoundary() {
        val start = 1000L

        assertTrue(
            policy.isSocketUsable(start, start)
        )

        assertTrue(
            policy.isSocketUsable(
                start,
                start + policy.SOCKET_MAX_AGE_MS - 1L
            )
        )

        assertFalse(
            policy.isSocketUsable(
                start,
                start + policy.SOCKET_MAX_AGE_MS
            )
        )

        assertFalse(
            policy.isSocketUsable(
                start,
                Long.MAX_VALUE
            )
        )
    }

    @Test
    fun invalidSocketTimestampsAreRejected() {
        assertFalse(
            policy.isSocketUsable(-1L, 1000L)
        )

        assertFalse(
            policy.isSocketUsable(1000L, 999L)
        )

        assertFalse(
            policy.isSocketUsable(
                Long.MIN_VALUE,
                Long.MAX_VALUE
            )
        )
    }
}
