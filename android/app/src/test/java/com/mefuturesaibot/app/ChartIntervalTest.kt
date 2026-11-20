package com.mefuturesaibot.app

import com.mefuturesaibot.app.exchange.Candle
import org.junit.Assert.*
import org.junit.Test

class ChartIntervalTest {
    @Test fun fiveMinuteBarsUseExchangeBoundariesAndExcludePartialEdges() {
        val source = (2..11).map {
            val open = it.toBigDecimal()
            Candle(it * 60_000L, open, open + 2.toBigDecimal(), open - 1.toBigDecimal(),
                open + 1.toBigDecimal(), 2.toBigDecimal())
        }
        assertSame(source, chartCandles(source, 1))
        assertTrue(chartCandles(source, 15).isEmpty())
        val candle = chartCandles(source, 5).single()
        assertEquals(300000L, candle.openTime)
        assertEquals("5", candle.open.toPlainString())
        assertEquals("11", candle.high.toPlainString())
        assertEquals("4", candle.low.toPlainString())
        assertEquals("10", candle.close.toPlainString())
        assertEquals("10", candle.volume.toPlainString())
    }
}
