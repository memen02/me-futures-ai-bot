package com.mefuturesaibot.app.account

import android.os.SystemClock
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Shared account-request cooldown.
 *
 * Uses monotonic time for cooldown deadlines.
 * Must be shared by all account display clients.
 *
 * Does not execute network requests or orders.
 */
internal class AccountRateLimitGuard(
    private val elapsed: () -> Long =
        { SystemClock.elapsedRealtime() },
    private val wallClock: () -> Long =
        { System.currentTimeMillis() }
) {

    private var blockedUntil = 0L

    @Synchronized
    fun remainingMs(): Long {
        val now = elapsed().coerceAtLeast(0L)

        return if (blockedUntil <= now) {
            0L
        } else {
            blockedUntil - now
        }
    }

    @Synchronized
    fun isAllowed(): Boolean {
        return remainingMs() == 0L
    }

    @Synchronized
    fun blockFor(delayMs: Long) {
        if (delayMs <= 0L) return

        val now = elapsed().coerceAtLeast(0L)

        val deadline = if (
            delayMs > Long.MAX_VALUE - now
        ) {
            Long.MAX_VALUE
        } else {
            now + delayMs
        }

        blockedUntil = maxOf(
            blockedUntil,
            deadline
        )
    }

    fun record(error: AccountListenKeyError) {
        blockFor(error.retryAfterMs)
    }

    fun record(error: AccountBalanceError) {
        blockFor(error.retryAfterMs)
    }

    fun record(failure: AccountSocketFailure) {
        val status = failure.httpStatus

        if (status == 418 || status == 429) {
            recordHttpLimit(
                status,
                failure.retryAfter
            )
        }
    }

    fun recordHttpLimit(
        status: Int,
        retryAfter: String?
    ) {
        val minimum = when (status) {
            418 -> 300000L
            429 -> 120000L
            else -> return
        }

        val requested = parseRetryAfter(
            retryAfter
        )

        blockFor(
            maxOf(minimum, requested)
        )
    }

    private fun parseRetryAfter(
        value: String?
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
