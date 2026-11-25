package com.mefuturesaibot.app.account

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class AccountRateLimitGuardTest {

    private var now = 1000L

    private var wall = 1700000000000L

    private val guard = AccountRateLimitGuard(
        elapsed = { now },
        wallClock = { wall }
    )

    private fun httpDate(
        timestamp: Long
    ): String {
        return Instant.ofEpochMilli(timestamp)
            .atZone(ZoneOffset.UTC)
            .format(
                DateTimeFormatter.RFC_1123_DATE_TIME
            )
    }

    @Test
    fun initiallyAllowsRequests() {
        assertTrue(guard.isAllowed())
        assertEquals(0L, guard.remainingMs())
    }

    @Test
    fun positiveCooldownBlocksRequests() {
        guard.blockFor(5000L)

        assertFalse(guard.isAllowed())
        assertEquals(5000L, guard.remainingMs())
    }

    @Test
    fun cooldownExpiresAtExactDeadline() {
        guard.blockFor(5000L)

        now += 4999L

        assertFalse(guard.isAllowed())
        assertEquals(1L, guard.remainingMs())

        now += 1L

        assertTrue(guard.isAllowed())
        assertEquals(0L, guard.remainingMs())
    }

    @Test
    fun shorterCooldownCannotReduceDeadline() {
        guard.blockFor(300000L)

        now += 10000L

        guard.blockFor(120000L)

        assertEquals(
            290000L,
            guard.remainingMs()
        )
    }

    @Test
    fun longerCooldownExtendsDeadline() {
        guard.blockFor(120000L)

        now += 10000L

        guard.blockFor(300000L)

        assertEquals(
            300000L,
            guard.remainingMs()
        )
    }

    @Test
    fun zeroAndNegativeCooldownAreIgnored() {
        guard.blockFor(0L)
        guard.blockFor(-1L)
        guard.blockFor(Long.MIN_VALUE)

        assertTrue(guard.isAllowed())

        guard.blockFor(5000L)
        guard.blockFor(-10000L)

        assertEquals(5000L, guard.remainingMs())
    }

    @Test
    fun http429UsesMinimumCooldown() {
        guard.recordHttpLimit(429, null)

        assertEquals(
            120000L,
            guard.remainingMs()
        )
    }

    @Test
    fun http418UsesMinimumCooldown() {
        guard.recordHttpLimit(418, null)

        assertEquals(
            300000L,
            guard.remainingMs()
        )
    }

    @Test
    fun numericRetryAfterExtendsCooldown() {
        guard.recordHttpLimit(429, "180")

        assertEquals(
            180000L,
            guard.remainingMs()
        )
    }

    @Test
    fun numericRetryAfterCannotReduceMinimum() {
        guard.recordHttpLimit(418, "10")

        assertEquals(
            300000L,
            guard.remainingMs()
        )
    }

    @Test
    fun invalidRetryAfterUsesMinimum() {
        guard.recordHttpLimit(
            429,
            "invalid-sensitive-text"
        )

        assertEquals(
            120000L,
            guard.remainingMs()
        )
    }

    @Test
    fun unrelatedHttpStatusIsIgnored() {
        for (status in listOf(
            200, 400, 401, 403, 404, 500, 503
        )) {
            guard.recordHttpLimit(
                status,
                "600"
            )
        }

        assertTrue(guard.isAllowed())
    }

    @Test
    fun futureHttpDateExtendsCooldown() {
        guard.recordHttpLimit(
            429,
            httpDate(wall + 600000L)
        )

        assertEquals(
            600000L,
            guard.remainingMs()
        )
    }

    @Test
    fun expiredHttpDateUsesMinimum() {
        guard.recordHttpLimit(
            429,
            httpDate(wall - 600000L)
        )

        assertEquals(
            120000L,
            guard.remainingMs()
        )
    }

    @Test
    fun wallClockChangesDoNotAffectActiveCooldown() {
        guard.recordHttpLimit(
            429,
            httpDate(wall + 600000L)
        )

        wall += 86400000L

        assertEquals(
            600000L,
            guard.remainingMs()
        )

        wall -= 172800000L

        now += 1000L

        assertEquals(
            599000L,
            guard.remainingMs()
        )
    }

    @Test
    fun deadlineAdditionCannotOverflow() {
        now = Long.MAX_VALUE - 1000L

        guard.blockFor(5000L)

        assertEquals(
            1000L,
            guard.remainingMs()
        )

        assertFalse(guard.isAllowed())
    }

    @Test
    fun numericConversionCannotOverflow() {
        guard.recordHttpLimit(
            429,
            Long.MAX_VALUE.toString()
        )

        assertEquals(
            Long.MAX_VALUE - now,
            guard.remainingMs()
        )
    }

    @Test
    fun oversizedNumericHeaderSaturates() {
        guard.recordHttpLimit(
            418,
            "9".repeat(100)
        )

        assertEquals(
            Long.MAX_VALUE - now,
            guard.remainingMs()
        )
    }

    @Test
    fun recordsListenKeyError() {
        guard.record(
            AccountListenKeyError(
                explanation = "Mock rate limit",
                retryAfterMs = 240000L
            )
        )

        assertEquals(
            240000L,
            guard.remainingMs()
        )
    }

    @Test
    fun recordsBalanceError() {
        guard.record(
            AccountBalanceError(
                explanation = "Mock rate limit",
                retryAfterMs = 360000L
            )
        )

        assertEquals(
            360000L,
            guard.remainingMs()
        )
    }

    @Test
    fun recordsWebSocketFailure() {
        guard.record(
            AccountSocketFailure(
                httpStatus = 429,
                retryAfter = "600"
            )
        )

        assertEquals(
            600000L,
            guard.remainingMs()
        )
    }

    @Test
    fun ordinaryNetworkErrorsDoNotBlock() {
        guard.record(
            AccountListenKeyError(
                "Mock connection failure"
            )
        )

        guard.record(
            AccountBalanceError(
                "Mock connection failure"
            )
        )

        guard.record(AccountSocketFailure())

        assertTrue(guard.isAllowed())
    }

    @Test
    fun allErrorSourcesPreserveLongestCooldown() {
        guard.record(
            AccountBalanceError(
                "Mock balance limit",
                600000L
            )
        )

        guard.record(
            AccountListenKeyError(
                "Mock session limit",
                120000L
            )
        )

        guard.record(
            AccountSocketFailure(418, "300")
        )

        assertEquals(
            600000L,
            guard.remainingMs()
        )
    }

    @Test
    fun concurrentUpdatesPreserveLongestDeadline() {
        val worker = Executors.newFixedThreadPool(4)

        val start = CountDownLatch(1)

        try {
            val jobs = (1..100).map { index ->
                worker.submit {
                    check(
                        start.await(
                            5L,
                            TimeUnit.SECONDS
                        )
                    )

                    guard.blockFor(
                        index * 1000L
                    )
                }
            }

            start.countDown()

            jobs.forEach {
                it.get(
                    5L,
                    TimeUnit.SECONDS
                )
            }

            assertEquals(
                100000L,
                guard.remainingMs()
            )

            assertFalse(guard.isAllowed())

        } finally {
            worker.shutdownNow()
        }
    }

    @Test
    fun httpDateSubtractionCannotOverflow() {
        val future = httpDate(wall + 600000L)

        wall = Long.MIN_VALUE

        guard.recordHttpLimit(429, future)

        assertEquals(
            Long.MAX_VALUE - now,
            guard.remainingMs()
        )
    }
}
