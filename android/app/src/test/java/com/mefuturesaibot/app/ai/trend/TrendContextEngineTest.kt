package com.mefuturesaibot.app.ai.trend

import com.mefuturesaibot.app.ai.Timeframe
import com.mefuturesaibot.app.ai.TimeframeHistory
import com.mefuturesaibot.app.exchange.Candle
import org.junit.Test
import org.junit.Assert.*

class TrendContextEngineTest {
    @Test fun flatHistoryIsWeakAndDoesNotConfirmEntry() {
        val candles = List(60) { i ->
            Candle(i*60000L,100.toBigDecimal(),101.toBigDecimal(),99.toBigDecimal(),100.toBigDecimal(),1.toBigDecimal())
        }
        val h = TimeframeHistory("BTCUSDT",Timeframe.M1,candles,3600000L)
        val result = TrendContextEngine.calculate(h,h.observedAt)!!
        assertEquals(TrendDirection.SIDEWAYS,result.trend.direction)
        assertEquals(TrendEvent.WAIT,result.event)
        assertEquals(VolatilityRegime.NORMAL,result.volatility)
        assertEquals(1.0,result.relativeVolume!!,1e-9)
        assertNull(TrendContextEngine.calculate(h,h.observedAt+60000L))
    }
}
