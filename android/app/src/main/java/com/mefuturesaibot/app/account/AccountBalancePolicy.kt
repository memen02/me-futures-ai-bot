package com.mefuturesaibot.app.account

/**
 * Scheduling policy for display-only account balances.
 *
 * All timestamps use monotonic elapsed time.
 *
 * This policy does not perform network requests,
 * manage credentials or execute orders.
 */
internal object AccountBalancePolicy {

    const val DISPLAY_REFRESH_MS = 1_000L

    const val NORMAL_REFRESH_MS = 10_000L

    const val MIN_READ_INTERVAL_MS = 3_000L

    const val EVENT_DEBOUNCE_MS = 500L

    const val MIN_RETRY_MS = 3_000L

    const val MAX_RETRY_MS = 60_000L

    const val MAX_SCHEDULE_DELAY_MS = 60_000L

    const val MAX_RETRY_ATTEMPT = 5

    /**
     * Exponential retry with bounded positive jitter.
     *
     * Attempt zero is the first retry.
     */
    fun retryDelayMs(
        attempt: Int,
        jitterPercent: Int
    ): Long {
        val exponent =
            attempt.coerceIn(0, MAX_RETRY_ATTEMPT)

        val base = minOf(
            MIN_RETRY_MS * (1L shl exponent),
            MAX_RETRY_MS
        )

        val jitter =
            jitterPercent.coerceIn(0, 30)

        return minOf(
            base + base * jitter / 100L,
            MAX_RETRY_MS
        )
    }

    /**
     * Safely calculate an elapsed-time deadline.
     */
    fun deadline(
        now: Long,
        delayMs: Long
    ): Long {
        require(now >= 0L)
        require(delayMs >= 0L)

        return if (
            delayMs > Long.MAX_VALUE - now
        ) {
            Long.MAX_VALUE
        } else {
            now + delayMs
        }
    }

    /**
     * Remaining delay until a monotonic deadline.
     */
    fun remainingMs(
        now: Long,
        deadline: Long
    ): Long {
        require(now >= 0L)
        require(deadline >= 0L)

        return if (deadline <= now) {
            0L
        } else {
            deadline - now
        }
    }

    /**
     * Minimum interval between REST read starts.
     *
     * Minus one represents no previous read.
     * Clock rollback must not permit an early read.
     */
    fun minimumReadDelayMs(
        lastReadStartedAt: Long,
        now: Long
    ): Long {
        require(now >= 0L)
        require(lastReadStartedAt >= -1L)

        if (lastReadStartedAt == -1L) {
            return 0L
        }

        return remainingMs(
            now,
            deadline(
                lastReadStartedAt,
                MIN_READ_INTERVAL_MS
            )
        )
    }

    /**
     * Combine scheduling constraints without
     * shortening the shared rate-limit cooldown.
     */
    fun nextReadDelayMs(
        requestedDelayMs: Long,
        minimumIntervalDelayMs: Long,
        retryDelayMs: Long,
        cooldownMs: Long
    ): Long {
        require(requestedDelayMs >= 0L)
        require(minimumIntervalDelayMs >= 0L)
        require(retryDelayMs >= 0L)
        require(cooldownMs >= 0L)

        return maxOf(
            requestedDelayMs,
            minimumIntervalDelayMs,
            retryDelayMs,
            cooldownMs
        )
    }

    /**
     * Bound individual scheduler waits.
     *
     * The controller must recheck the original
     * deadline and cooldown after every wakeup.
     */
    fun scheduleDelayMs(
        remainingMs: Long
    ): Long {
        require(remainingMs >= 0L)

        return minOf(
            remainingMs,
            MAX_SCHEDULE_DELAY_MS
        )
    }
}
