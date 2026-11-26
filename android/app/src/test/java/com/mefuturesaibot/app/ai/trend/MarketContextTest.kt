package com.mefuturesaibot.app.ai.trend

import com.mefuturesaibot.app.ai.Timeframe
import com.mefuturesaibot.app.ai.TimeframeHistory
import com.mefuturesaibot.app.exchange.Candle
import org.junit.Test
import org.junit.Assert.*

class MarketContextTest {
    private fun history(prices: List<Int>, volumes: List<Int> = prices.map { 1 }): TimeframeHistory {
        val candles = prices.mapIndexed { i,p ->
            val v = p.toBigDecimal()
            Candle(i*60000L,v,v,v,v,volumes[i].toBigDecimal())
        }
        return TimeframeHistory("BTCUSDT",Timeframe.M1,candles,candles.size*60000L)
    }
    @Test fun relativeVolumeExcludesCurrentFromBaseline() {
        val h = history(listOf(100,100,100),listOf(2,2,6))
        assertEquals(3.0,RelativeVolumeEngine.calculate(h,h.observedAt,2)!!,1e-9)
        assertNull(RelativeVolumeEngine.calculate(h,h.observedAt,3))
        assertNull(RelativeVolumeEngine.calculate(h,h.observedAt+60000L,2))
    }
    @Test fun failedBreakoutReturnsInsideLevel() {
        val h = history(listOf(100,110,100,115,105))
        assertEquals(FailedBreakout.UP,FailedBreakoutEngine.detect(h,h.observedAt,1))
        val sustained = history(listOf(100,110,100,115,120))
        assertNull(FailedBreakoutEngine.detect(sustained,sustained.observedAt,1))
    }
}
