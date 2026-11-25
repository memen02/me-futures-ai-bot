package com.mefuturesaibot.app.account

import android.os.SystemClock

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
        blockFor(
            AccountRetryAfter.delayMs(
                status,
                retryAfter,
                wallClock
            )
        )
    }
}
