package com.mefuturesaibot.app.ai

internal class AiExchangeClock(
    private val maxAgeMs: Long = 30_000L,
    private val maxRoundTripMs: Long = 2_000L
) {
    init {
        require(maxAgeMs > 0L)
        require(maxRoundTripMs > 0L)
    }

    internal data class Bounds(
        val earliest: Long,
        val latest: Long
    )

    private data class Anchor(
        val serverTime: Long,
        val received: Long,
        val roundTrip: Long
    )

    private var anchor: Anchor? = null

    @Synchronized
    fun observe(
        serverTime: Long,
        sentElapsed: Long,
        receivedElapsed: Long
    ): Boolean {
        if (
            serverTime <= 0L ||
            sentElapsed < 0L ||
            receivedElapsed < sentElapsed
        ) {
            anchor = null
            return false
        }

        val roundTrip =
            receivedElapsed - sentElapsed

        if (roundTrip > maxRoundTripMs) {
            anchor = null
            return false
        }

        val previous = anchor

        if (
            previous != null &&
            receivedElapsed < previous.received
        ) {
            return false
        }

        anchor = Anchor(
            serverTime,
            receivedElapsed,
            roundTrip
        )

        return true
    }

    @Synchronized
    fun bounds(elapsed: Long): Bounds? {
        val current = anchor ?: return null

        if (elapsed < current.received) {
            return null
        }

        val age = elapsed - current.received

        if (age > maxAgeMs) {
            return null
        }

        return try {
            val earliest = Math.addExact(
                current.serverTime,
                age
            )

            val latest = Math.addExact(
                earliest,
                current.roundTrip
            )

            Bounds(earliest, latest)
        } catch (_: ArithmeticException) {
            null
        }
    }

    @Synchronized
    fun clear() {
        anchor = null
    }
}
