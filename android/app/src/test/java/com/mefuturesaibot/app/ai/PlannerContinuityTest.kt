package com.mefuturesaibot.app.ai

import org.junit.Assert.*
import org.junit.Test

class PlannerContinuityTest {

    @Test
    fun preservesNextSymbolAfterReplacement() {
        val planner = MultiTimeframePlanner()

        planner.updateSymbols(
            listOf("BTCUSDT", "ETHUSDT", "SOLUSDT")
        )

        assertEquals(
            "BTCUSDT",
            planner.next(emptyMap())?.symbol
        )

        planner.updateSymbols(
            listOf("BTCUSDT", "ETHUSDT", "BNBUSDT")
        )

        val task = requireNotNull(
            planner.next(emptyMap())
        )

        assertEquals("ETHUSDT", task.symbol)
        assertEquals(Timeframe.D1, task.timeframe)
    }

    @Test
    fun preservesNextSymbolWhenItsIndexChanges() {
        val planner = MultiTimeframePlanner()

        planner.updateSymbols(
            listOf("BTCUSDT", "ETHUSDT", "SOLUSDT")
        )

        planner.next(emptyMap())

        planner.updateSymbols(
            listOf("BNBUSDT", "SOLUSDT", "ETHUSDT")
        )

        val task = requireNotNull(
            planner.next(emptyMap())
        )

        assertEquals("ETHUSDT", task.symbol)
        assertEquals(Timeframe.D1, task.timeframe)
    }

    @Test
    fun removedNextSymbolUsesValidFallback() {
        val planner = MultiTimeframePlanner()

        planner.updateSymbols(
            listOf("BTCUSDT", "ETHUSDT", "SOLUSDT")
        )

        planner.next(emptyMap())
        planner.next(emptyMap())

        planner.updateSymbols(
            listOf("BTCUSDT", "BNBUSDT")
        )

        val task = requireNotNull(
            planner.next(emptyMap())
        )

        assertEquals("BTCUSDT", task.symbol)
        assertEquals(Timeframe.D1, task.timeframe)
    }
}
