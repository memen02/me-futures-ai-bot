package com.mefuturesaibot.app.ai

import org.junit.Assert.*
import org.junit.Test
import com.mefuturesaibot.app.ai.MultiTimeframeRepository.Status

class MultiTimeframeSchedulerCoreTest {

    private fun core() =
        MultiTimeframeSchedulerCore().apply {
            updateSymbols(listOf("BTCUSDT"))
        }

    private val task =
        MultiTimeframePlanner.Task(
            "BTCUSDT",
            Timeframe.D1
        )

    @Test
    fun noCandidatesWaitsWithoutRequest() {
        val core = MultiTimeframeSchedulerCore()
        val decision = core.next(emptyMap(), 0L)

        assertNull(decision.task)
        assertEquals(5_000L, decision.delayMs)
        assertFalse(decision.stopped)
    }

    @Test
    fun selectsMissingTimeframe() {
        val decision = core().next(emptyMap(), 0L)

        assertEquals(task, decision.task)
        assertEquals(0L, decision.delayMs)
    }

    @Test
    fun freshDataDoesNotNeedRequest() {
        val decision = core().next(
            mapOf(
                "BTCUSDT" to Timeframe.entries.toSet()
            ),
            0L
        )

        assertNull(decision.task)
        assertEquals(5_000L, decision.delayMs)
    }

    @Test
    fun successfulRefreshUsesMinimumDelay() {
        assertEquals(
            500L,
            core().complete(task, Status.UPDATED, 0L)
        )
    }

    @Test
    fun cachedDataUsesMinimumDelay() {
        assertEquals(
            500L,
            core().complete(task, Status.CACHED, 0L)
        )
    }

    @Test
    fun clockSynchronizationWaits() {
        assertEquals(
            500L,
            core().complete(task, Status.WAIT_CLOCK, 0L)
        )
    }

    @Test
    fun rateLimitUsesLongerDelay() {
        assertEquals(
            5_000L,
            core().complete(task, Status.WAIT_LIMIT, 0L)
        )
    }

    @Test
    fun missingDataTemporarilyDefersTask() {
        val core = core()

        assertEquals(
            2_000L,
            core.complete(task, Status.WAIT_DATA, 0L)
        )

        val available = mapOf(
            "BTCUSDT" to
                (Timeframe.entries.toSet() - Timeframe.D1)
        )

        assertNull(
            core.next(available, 1_999L).task
        )

        assertEquals(
            task,
            core.next(available, 2_000L).task
        )
    }

    @Test
    fun repeatedFailuresIncreaseBackoff() {
        val core = core()

        val delays = List(8) {
            core.complete(
                task,
                Status.FAILED,
                it * 60_000L
            )
        }

        assertEquals(
            listOf(
                2_000L,
                4_000L,
                8_000L,
                16_000L,
                32_000L,
                60_000L,
                60_000L,
                60_000L
            ),
            delays
        )
    }

    @Test
    fun successResetsFailureBackoff() {
        val core = core()

        core.complete(task, Status.FAILED, 0L)
        core.complete(task, Status.FAILED, 2_000L)
        core.complete(task, Status.UPDATED, 6_000L)

        assertEquals(
            2_000L,
            core.complete(
                task,
                Status.FAILED,
                7_000L
            )
        )
    }

    @Test
    fun candidateRemovalClearsDeferredTask() {
        val core = core()

        core.complete(task, Status.WAIT_DATA, 0L)

        core.updateSymbols(listOf("ETHUSDT"))
        core.updateSymbols(listOf("BTCUSDT"))

        assertEquals(
            task,
            core.next(emptyMap(), 100L).task
        )
    }

    @Test
    fun repositoryClosedStopsScheduler() {
        val core = core()

        assertEquals(
            0L,
            core.complete(task, Status.CLOSED, 0L)
        )

        assertTrue(
            core.next(emptyMap(), 0L).stopped
        )
    }

    @Test
    fun closedSchedulerCannotRestart() {
        val core = core()

        core.close()
        core.updateSymbols(listOf("ETHUSDT"))

        assertTrue(core.activeSymbols().isEmpty())
        assertTrue(core.next(emptyMap(), 0L).stopped)
        assertNull(core.next(emptyMap(), 0L).task)
    }
}
