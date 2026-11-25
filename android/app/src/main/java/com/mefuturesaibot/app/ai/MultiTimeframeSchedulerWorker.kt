package com.mefuturesaibot.app.ai

import android.os.SystemClock
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.ScheduledFuture

internal class MultiTimeframeSchedulerWorker(
    private val repository: MultiTimeframeRepository =
        MultiTimeframeRepository(),
    private val core: MultiTimeframeSchedulerCore =
        MultiTimeframeSchedulerCore(),
    private val elapsed: () -> Long =
        { SystemClock.elapsedRealtime() }
) : AutoCloseable {

    private val lifecycle = Any()

    private val worker =
        Executors.newSingleThreadScheduledExecutor()

    private var pending: ScheduledFuture<*>? = null

    private var started = false

    private var generation = 0L

    @Volatile
    private var closed = false

    fun updateSymbols(candidates: List<String>) {
        synchronized(lifecycle) {
            if (closed) return

            val previous =
                core.activeSymbols()

            core.updateSymbols(candidates)

            if (previous != core.activeSymbols()) {
                generation++
            }
        }
    }

    fun start() {
        synchronized(lifecycle) {
            if (closed || started) return

            started = true

            scheduleLocked(0L)
        }
    }

    private fun scheduleLocked(delayMs: Long) {
        if (closed || !started) return

        pending = worker.schedule(
            { cycle() },
            delayMs.coerceAtLeast(0L),
            TimeUnit.MILLISECONDS
        )
    }

    private fun cycle() {
        if (closed) return

        val delay = try {
            runCycle()
        } catch (_: Exception) {
            60_000L
        }

        synchronized(lifecycle) {
            if (!closed) {
                scheduleLocked(delay)
            }
        }
    }

    private fun runCycle(): Long {
        val (currentGeneration, symbols) =
            synchronized(lifecycle) {
                if (closed) return 0L

                generation to core.activeSymbols()
            }

        if (symbols.isEmpty()) {
            return 5_000L
        }

        val cooldown =
            repository.cooldownRemaining()

        if (cooldown > 0L) {
            return cooldown.coerceAtLeast(500L)
        }

        val available = symbols.associateWith {
            repository.availableFrames(it)
        }

        val decision = core.next(
            available,
            elapsed()
        )

        if (decision.stopped) {
            close()
            return 0L
        }

        val task = decision.task
            ?: return decision.delayMs

        synchronized(lifecycle) {
            if (closed) return 0L

            if (
                generation != currentGeneration ||
                task.symbol !in core.activeSymbols()
            ) {
                return 500L
            }
        }

        val status = repository.refresh(
            task.symbol,
            task.timeframe
        )

        if (closed) return 0L

        if (
            status ==
            MultiTimeframeRepository.Status.CLOSED
        ) {
            close()
            return 0L
        }

        val delay = synchronized(lifecycle) {
            if (closed) return 0L

            if (generation != currentGeneration) {
                return maxOf(
                    500L,
                    repository.cooldownRemaining()
                )
            }

            core.complete(
                task,
                status,
                elapsed()
            )
        }

        return maxOf(
            delay,
            repository.cooldownRemaining(),
            500L
        )
    }

    fun snapshot(
        symbol: String
    ): Map<Timeframe, TimeframeHistory> {
        synchronized(lifecycle) {
            if (closed) return emptyMap()

            if (symbol !in core.activeSymbols()) {
                return emptyMap()
            }

            return repository.snapshot(symbol)
        }
    }

    fun isRunning(): Boolean {
        synchronized(lifecycle) {
            return started && !closed
        }
    }

    override fun close() {
        synchronized(lifecycle) {
            if (closed) return

            closed = true
            started = false
            generation++

            pending?.cancel(false)
            pending = null

            core.close()
            repository.close()

            worker.shutdownNow()
        }
    }
}
