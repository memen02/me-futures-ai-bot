package com.mefuturesaibot.app.account

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Shared, overflow-safe Retry-After calculation.
 *
 * Returns a duration, not a wall-clock deadline.
 * Does not execute requests or retain response data.
 */
internal object AccountRetryAfter {

    fun delayMs(
        status: Int,
        retryAfter: String?,
        wallClock: () -> Long = {
            System.currentTimeMillis()
        }
    ): Long {

        val minimum = when (status) {
            418 -> 300000L
            429 -> 120000L
            else -> return 0L
        }

        return maxOf(
            minimum,
            parse(retryAfter, wallClock)
        )
    }

    private fun parse(
        value: String?,
        wallClock: () -> Long
    ): Long {

        val raw = value?.trim() ?: return 0L

        if (raw.isEmpty()) return 0L

        if (raw.all { it in '0'..'9' }) {

            val seconds = raw.toLongOrNull()
                ?: return Long.MAX_VALUE

            return if (
                seconds > Long.MAX_VALUE / 1000L
            ) {
                Long.MAX_VALUE
            } else {
                seconds * 1000L
            }
        }

        if (raw.length > 128) return 0L

        return runCatching {

            val target = ZonedDateTime.parse(
                raw,
                DateTimeFormatter.RFC_1123_DATE_TIME
            ).toInstant().toEpochMilli()

            val now = wallClock()

            if (target <= now) {
                0L
            } else {
                try {
                    Math.subtractExact(target, now)
                } catch (_: ArithmeticException) {
                    Long.MAX_VALUE
                }
            }

        }.getOrDefault(0L)
    }
}
