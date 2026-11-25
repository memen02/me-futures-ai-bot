package com.mefuturesaibot.app.account

/**
 * Timing policy for private account sessions.
 *
 * No network, credentials, orders or Android
 * lifecycle dependencies.
 */
internal object AccountSessionPolicy {

    const val KEEPALIVE_MS = 1800000L

    const val KEY_MAX_AGE_MS = 3300000L

    const val SOCKET_MAX_AGE_MS = 82800000L

    const val CONNECT_TIMEOUT_MS = 15000L

    const val MIN_RECONNECT_MS = 2000L

    const val MAX_RECONNECT_MS = 60000L

    const val MAX_SCHEDULE_DELAY_MS = 60000L

    fun reconnectDelayMs(
        attempt: Int,
        jitterPercent: Int = 0
    ): Long {

        val safeAttempt =
            attempt.coerceIn(0, 5)

        val base = (
            MIN_RECONNECT_MS *
                (1L shl safeAttempt)
            ).coerceAtMost(MAX_RECONNECT_MS)

        val jitter =
            base * jitterPercent.coerceIn(0, 30) / 100L

        return (base + jitter)
            .coerceAtMost(MAX_RECONNECT_MS)
    }

    fun retryDelayMs(
        attempt: Int,
        cooldownRemainingMs: Long,
        jitterPercent: Int = 0
    ): Long {

        return maxOf(
            reconnectDelayMs(
                attempt,
                jitterPercent
            ),
            cooldownRemainingMs.coerceAtLeast(0L)
        )
    }

    /**
     * Long cooldowns are checked in bounded chunks.
     * A wakeup is not permission to send a request.
     * The manager must check the guard again.
     */
    fun scheduleDelayMs(
        remainingMs: Long
    ): Long {

        return remainingMs.coerceIn(
            0L,
            MAX_SCHEDULE_DELAY_MS
        )
    }

    fun isKeyUsable(
        lastRenewedElapsedMs: Long,
        nowElapsedMs: Long
    ): Boolean {

        return isWithinAge(
            lastRenewedElapsedMs,
            nowElapsedMs,
            KEY_MAX_AGE_MS
        )
    }

    fun isSocketUsable(
        connectedElapsedMs: Long,
        nowElapsedMs: Long
    ): Boolean {

        return isWithinAge(
            connectedElapsedMs,
            nowElapsedMs,
            SOCKET_MAX_AGE_MS
        )
    }

    private fun isWithinAge(
        started: Long,
        now: Long,
        maximumAge: Long
    ): Boolean {

        if (started < 0L || now < started) {
            return false
        }

        return now - started < maximumAge
    }
}
