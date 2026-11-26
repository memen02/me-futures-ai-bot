package com.mefuturesaibot.app.ai.indicators
import com.mefuturesaibot.app.ai.Timeframe
import com.mefuturesaibot.app.ai.TimeframeHistory
import com.mefuturesaibot.app.exchange.Candle
import org.junit.Test
import org.junit.Assert.*

class AtrEngineTest {
    @Test fun constantRange() {
        val candles = List(15) { i -> Candle(i*60000L, 100.toBigDecimal(), 102.toBigDecimal(), 98.toBigDecimal(), 100.toBigDecimal(), 1.toBigDecimal()) }
        val history = TimeframeHistory("BTCUSDT", Timeframe.M1, candles, 900000L)
        assertEquals(4.0, AtrEngine.calculate(history, 900000L)!!, 1e-9)
        assertNull(AtrEngine.calculate(history, 960000L))
        assertNull(AtrEngine.calculate(history, 900000L, 15))
    }
}
