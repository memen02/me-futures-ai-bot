package com.mefuturesaibot.app.ai.indicators
import com.mefuturesaibot.app.ai.Timeframe
import com.mefuturesaibot.app.ai.TimeframeHistory
import com.mefuturesaibot.app.exchange.Candle
import org.junit.Test
import org.junit.Assert.*

class RollingVwapEngineTest {
    @Test fun weightsByVolumeAndRejectsZeroVolume() {
        val candles = listOf(
            Candle(0L, 100.toBigDecimal(), 100.toBigDecimal(), 100.toBigDecimal(), 100.toBigDecimal(), 1.toBigDecimal()),
            Candle(60000L, 200.toBigDecimal(), 200.toBigDecimal(), 200.toBigDecimal(), 200.toBigDecimal(), 3.toBigDecimal())
        )
        val history = TimeframeHistory("BTCUSDT", Timeframe.M1, candles, 120000L)
        assertEquals(175.0, RollingVwapEngine.calculate(history, 120000L, 2)!!, 1e-9)
        assertNull(RollingVwapEngine.calculate(history, 120000L, 3))
        assertNull(RollingVwapEngine.calculate(history.copy(candles = candles.map { it.copy(volume = 0.toBigDecimal()) }), 120000L, 2))
    }
}
