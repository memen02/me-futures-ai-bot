package com.mefuturesaibot.app.account

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.junit.Assert.*
import org.junit.Test

class AccountRetryAfterTest {

    private val engine = AccountRetryAfter

    private val now = 1700000000000L

    private fun date(timestamp: Long): String {
        return Instant.ofEpochMilli(timestamp)
            .atZone(ZoneOffset.UTC)
            .format(DateTimeFormatter.RFC_1123_DATE_TIME)
    }

    @Test
    fun minimum429() {
        assertEquals(
            120000L,
            engine.delayMs(429, null)
        )
    }

    @Test
    fun minimum418() {
        assertEquals(
            300000L,
            engine.delayMs(418, null)
        )
    }

    @Test
    fun unrelatedStatusesAreIgnored() {
        for (status in listOf(200, 400, 401, 403, 500)) {
            assertEquals(
                0L,
                engine.delayMs(status, "600")
            )
        }
    }

    @Test
    fun numericHeaderExtendsCooldown() {
        assertEquals(
            600000L,
            engine.delayMs(429, "600")
        )
    }

    @Test
    fun numericHeaderCannotReduceMinimum() {
        assertEquals(
            300000L,
            engine.delayMs(418, "1")
        )
    }

    @Test
    fun longCooldownIsNotClampedToOneDay() {
        assertEquals(
            172800000L,
            engine.delayMs(429, "172800")
        )
    }

    @Test
    fun whitespaceIsAccepted() {
        assertEquals(
            600000L,
            engine.delayMs(429, " 600 ")
        )
    }

    @Test
    fun negativeHeaderUsesMinimum() {
        assertEquals(
            120000L,
            engine.delayMs(429, "-600")
        )
    }

    @Test
    fun zeroHeaderUsesMinimum() {
        assertEquals(
            120000L,
            engine.delayMs(429, "0")
        )
    }

    @Test
    fun invalidHeaderUsesMinimum() {
        assertEquals(
            120000L,
            engine.delayMs(429, "invalid")
        )
    }

    @Test
    fun blankHeaderUsesMinimum() {
        assertEquals(
            120000L,
            engine.delayMs(429, " ")
        )
    }

    @Test
    fun numericMultiplicationCannotOverflow() {
        assertEquals(
            Long.MAX_VALUE,
            engine.delayMs(
                429,
                Long.MAX_VALUE.toString()
            )
        )
    }

    @Test
    fun oversizedNumericHeaderSaturates() {
        assertEquals(
            Long.MAX_VALUE,
            engine.delayMs(
                418,
                "9".repeat(100)
            )
        )
    }

    @Test
    fun largestSafeSecondsArePreserved() {
        val seconds = Long.MAX_VALUE / 1000L

        assertEquals(
            seconds * 1000L,
            engine.delayMs(
                429,
                seconds.toString()
            )
        )
    }

    @Test
    fun futureHttpDateExtendsCooldown() {
        assertEquals(
            600000L,
            engine.delayMs(
                429,
                date(now + 600000L)
            ) { now }
        )
    }

    @Test
    fun expiredHttpDateUsesMinimum() {
        assertEquals(
            120000L,
            engine.delayMs(
                429,
                date(now - 600000L)
            ) { now }
        )
    }

    @Test
    fun httpDateCannotReduceMinimum() {
        assertEquals(
            300000L,
            engine.delayMs(
                418,
                date(now + 1000L)
            ) { now }
        )
    }

    @Test
    fun httpDateSubtractionCannotOverflow() {
        assertEquals(
            Long.MAX_VALUE,
            engine.delayMs(
                429,
                date(now + 600000L)
            ) { Long.MIN_VALUE }
        )
    }

    @Test
    fun oversizedInvalidDateUsesMinimum() {
        assertEquals(
            120000L,
            engine.delayMs(
                429,
                "x".repeat(200)
            )
        )
    }

    @Test
    fun failedWallClockUsesMinimum() {
        assertEquals(
            120000L,
            engine.delayMs(
                429,
                date(now + 600000L)
            ) {
                error("Mock clock failure")
            }
        )
    }
}
