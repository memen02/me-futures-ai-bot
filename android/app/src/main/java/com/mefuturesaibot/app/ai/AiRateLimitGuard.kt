package com.mefuturesaibot.app.ai

internal class AiRateLimitGuard(
    private val minSpacingMs: Long = 500L,
    private val weightBudget: Int = 300
) {
    init {
        require(minSpacingMs >= 100L)
        require(weightBudget > 0)
    }

    private data class Usage(
        val at: Long,
        val weight: Int
    )

    private val usage = ArrayDeque<Usage>()

    private var nextAllowed = 0L
    private var cooldownUntil = 0L
    private var busy = false

    private fun prune(now: Long) {
        while (
            usage.isNotEmpty() &&
            now - usage.first().at >= 60_000L
        ) {
            usage.removeFirst()
        }
    }

    @Synchronized
    fun acquire(
        now: Long,
        weight: Int
    ): Boolean {
        require(now >= 0L)
        require(weight in 1..weightBudget)

        prune(now)

        if (busy) return false
        if (now < cooldownUntil) return false
        if (now < nextAllowed) return false

        val used = usage.sumOf { it.weight }

        if (used.toLong() + weight > weightBudget) {
            return false
        }

        busy = true
        usage.addLast(Usage(now, weight))
        nextAllowed = now + minSpacingMs

        return true
    }

    @Synchronized
    fun release() {
        busy = false
    }

    @Synchronized
    fun onHttpResponse(
        code: Int,
        retryAfterSeconds: Long?,
        now: Long
    ) {
        require(now >= 0L)

        if (code == 429 || code == 418) {
            val fallback =
                if (code == 418) 3600L else 60L

            val seconds = maxOf(
                fallback,
                retryAfterSeconds ?: fallback
            )

            val delay = seconds.coerceAtMost(
                (Long.MAX_VALUE - now) / 1000L
            ) * 1000L

            cooldownUntil = maxOf(
                cooldownUntil,
                now + delay
            )
        }
    }

    @Synchronized
    fun cooldownRemaining(now: Long): Long =
        (cooldownUntil - now).coerceAtLeast(0L)

    @Synchronized
    fun usedWeight(now: Long): Int {
        prune(now)
        return usage.sumOf { it.weight }
    }
}
