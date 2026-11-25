package com.mefuturesaibot.app.ai

import org.junit.Assert.*
import org.junit.Test

class MultiTimeframePlannerTest {

    @Test
    fun noCandidatesReturnsNoTask() {
        val planner = MultiTimeframePlanner()

        assertNull(planner.next(emptyMap()))
    }

    @Test
    fun limitsActiveSymbols() {
        val planner = MultiTimeframePlanner()

        planner.updateSymbols(
            listOf(
                "BTCUSDT",
                "ETHUSDT",
                "SOLUSDT",
                "BNBUSDT"
            )
        )

        assertEquals(
            listOf(
                "BTCUSDT",
                "ETHUSDT",
                "SOLUSDT"
            ),
            planner.activeSymbols()
        )
    }

    @Test
    fun removesDuplicateAndBlankSymbols() {
        val planner = MultiTimeframePlanner()

        planner.updateSymbols(
            listOf(
                "",
                "BTCUSDT",
                "BTCUSDT",
                " ETHUSDT ",
                " "
            )
        )

        assertEquals(
            listOf("BTCUSDT", "ETHUSDT"),
            planner.activeSymbols()
        )
    }

    @Test
    fun coversAll15Timeframes() {
        val planner = MultiTimeframePlanner()

        planner.updateSymbols(
            listOf("BTCUSDT")
        )

        val tasks = List(15) {
            requireNotNull(
                planner.next(emptyMap())
            )
        }

        assertEquals(
            Timeframe.entries.toSet(),
            tasks.map { it.timeframe }.toSet()
        )

        assertEquals(15, tasks.toSet().size)
    }

    @Test
    fun rotatesSymbolsFairly() {
        val planner = MultiTimeframePlanner()

        planner.updateSymbols(
            listOf(
                "BTCUSDT",
                "ETHUSDT",
                "SOLUSDT"
            )
        )

        val tasks = List(45) {
            requireNotNull(
                planner.next(emptyMap())
            )
        }

        assertEquals(45, tasks.toSet().size)

        val counts = tasks.groupingBy {
            it.symbol
        }.eachCount()

        assertEquals(15, counts["BTCUSDT"])
        assertEquals(15, counts["ETHUSDT"])
        assertEquals(15, counts["SOLUSDT"])
    }

    @Test
    fun skipsFreshTimeframes() {
        val planner = MultiTimeframePlanner()

        planner.updateSymbols(
            listOf("BTCUSDT")
        )

        val available = mapOf(
            "BTCUSDT" to
                (Timeframe.entries.toSet() -
                    Timeframe.M1)
        )

        assertEquals(
            Timeframe.M1,
            planner.next(available)?.timeframe
        )
    }

    @Test
    fun returnsNullWhenAllFramesAreFresh() {
        val planner = MultiTimeframePlanner()

        planner.updateSymbols(
            listOf("BTCUSDT")
        )

        assertNull(
            planner.next(
                mapOf(
                    "BTCUSDT" to
                        Timeframe.entries.toSet()
                )
            )
        )
    }

    @Test
    fun skipsTemporarilyExcludedTasks() {
        val planner = MultiTimeframePlanner()

        planner.updateSymbols(
            listOf("BTCUSDT")
        )

        val blocked = MultiTimeframePlanner.Task(
            "BTCUSDT",
            Timeframe.D1
        )

        val task = planner.next(
            emptyMap(),
            setOf(blocked)
        )

        assertNotNull(task)
        assertNotEquals(blocked, task)
    }

    @Test
    fun rankingChangeDoesNotResetRotation() {
        val planner = MultiTimeframePlanner()

        planner.updateSymbols(
            listOf("BTCUSDT", "ETHUSDT")
        )

        assertEquals(
            "BTCUSDT",
            planner.next(emptyMap())?.symbol
        )

        planner.updateSymbols(
            listOf("ETHUSDT", "BTCUSDT")
        )

        assertEquals(
            "ETHUSDT",
            planner.next(emptyMap())?.symbol
        )
    }

    @Test
    fun clearRemovesPendingTasks() {
        val planner = MultiTimeframePlanner()

        planner.updateSymbols(
            listOf("BTCUSDT")
        )

        planner.clear()

        assertTrue(
            planner.activeSymbols().isEmpty()
        )

        assertNull(
            planner.next(emptyMap())
        )
    }
}
