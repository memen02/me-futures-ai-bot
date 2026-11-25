package com.mefuturesaibot.app.ai

internal class MultiTimeframeSchedulerCore(
    private val planner: MultiTimeframePlanner =
        MultiTimeframePlanner()
) {
    internal data class Decision(
        val task: MultiTimeframePlanner.Task?,
        val delayMs: Long,
        val stopped: Boolean = false
    )

    private var stopped = false
    private var failures = 0

    private val retryAt =
        mutableMapOf<MultiTimeframePlanner.Task, Long>()

    @Synchronized
    fun updateSymbols(candidates: List<String>) {
        if (stopped) return

        planner.updateSymbols(candidates)

        val active = planner.activeSymbols().toSet()

        retryAt.keys.retainAll {
            it.symbol in active
        }
    }

    @Synchronized
    fun activeSymbols(): List<String> =
        planner.activeSymbols()

    @Synchronized
    fun next(
        available: Map<String, Set<Timeframe>>,
        now: Long
    ): Decision {
        require(now >= 0L)

        if (stopped) {
            return Decision(null, 0L, true)
        }

        retryAt.entries.removeAll {
            it.value <= now
        }

        val task = planner.next(
            available,
            retryAt.keys.toSet()
        )

        return if (task == null) {
            Decision(null, 5_000L)
        } else {
            Decision(task, 0L)
        }
    }

    @Synchronized
    fun complete(
        task: MultiTimeframePlanner.Task,
        status: MultiTimeframeRepository.Status,
        now: Long
    ): Long {
        require(now >= 0L)

        if (stopped) return 0L

        return when (status) {
            MultiTimeframeRepository.Status.UPDATED,
            MultiTimeframeRepository.Status.CACHED -> {
                failures = 0
                retryAt.remove(task)
                500L
            }

            MultiTimeframeRepository.Status.WAIT_CLOCK -> {
                500L
            }

            MultiTimeframeRepository.Status.WAIT_LIMIT -> {
                5_000L
            }

            MultiTimeframeRepository.Status.WAIT_DATA -> {
                defer(task, now, 2_000L)
                2_000L
            }

            MultiTimeframeRepository.Status.FAILED -> {
                failures = (failures + 1)
                    .coerceAtMost(6)

                val delay = (
                    2_000L * (1L shl (failures - 1))
                ).coerceAtMost(60_000L)

                defer(task, now, delay)

                delay
            }

            MultiTimeframeRepository.Status.CLOSED -> {
                close()
                0L
            }
        }
    }

    private fun defer(
        task: MultiTimeframePlanner.Task,
        now: Long,
        delay: Long
    ) {
        if (task.symbol !in planner.activeSymbols()) {
            return
        }

        retryAt[task] = now +
            delay.coerceAtMost(Long.MAX_VALUE - now)
    }

    @Synchronized
    fun close() {
        stopped = true
        failures = 0
        retryAt.clear()
        planner.clear()
    }
}
