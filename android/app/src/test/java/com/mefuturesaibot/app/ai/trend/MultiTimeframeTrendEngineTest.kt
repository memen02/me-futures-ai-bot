package com.mefuturesaibot.app.ai.trend

import com.mefuturesaibot.app.ai.Timeframe
import com.mefuturesaibot.app.ai.TimeframeHistory
import com.mefuturesaibot.app.exchange.Candle
import org.junit.Test
import org.junit.Assert.*

class MultiTimeframeTrendEngineTest {
    private fun history(frame: Timeframe, duration: Long): TimeframeHistory {
        val candles = List(60) { i ->
            Candle(i*duration,100.toBigDecimal(),101.toBigDecimal(),99.toBigDecimal(),100.toBigDecimal(),1.toBigDecimal())
        }
        return TimeframeHistory("BTCUSDT",frame,candles,60*duration)
    }

    @Test fun coverageRejectsStaleAndMismatchedHistories() {
        val minute = history(Timeframe.M1,60000L)
        val five = history(Timeframe.M5,300000L)
        val result = MultiTimeframeTrendEngine.calculate("BTCUSDT",
            mapOf(Timeframe.M1 to minute,Timeframe.M5 to five,Timeframe.H1 to five),five.observedAt)
        assertEquals(setOf(Timeframe.M5),result.frames.keys)
        assertEquals(14,result.missing.size)
        assertEquals(TrendEvent.WAIT,result.frames[Timeframe.M5]!!.event)
        assertTrue(MultiTimeframeTrendEngine.calculate("ETHUSDT",mapOf(Timeframe.M5 to five),five.observedAt).frames.isEmpty())
    }
}
