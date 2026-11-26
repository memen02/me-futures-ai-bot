package com.mefuturesaibot.app.ai.indicators
import com.mefuturesaibot.app.ai.Timeframe
import com.mefuturesaibot.app.ai.TimeframeHistory
import com.mefuturesaibot.app.exchange.Candle
import org.junit.Test
import org.junit.Assert.*

class IndicatorSnapshotTest {
    private fun history(n: Int) = TimeframeHistory("BTCUSDT", Timeframe.M1,
        List(n) { i -> Candle(i*60000L, 100.toBigDecimal(), 101.toBigDecimal(), 99.toBigDecimal(), 100.toBigDecimal(), 1.toBigDecimal()) }, n*60000L)
    @Test fun warmupDoesNotInventValues() {
        val h = history(10)
        val s = IndicatorSnapshotEngine.calculate(h,h.observedAt)!!
        assertNull(s.ema50)
        assertNull(s.macd?.histogram)
        assertNull(s.atr14)
    }
    @Test fun completeHistoryHasExpectedValues() {
        val h = history(60)
        val s = IndicatorSnapshotEngine.calculate(h,h.observedAt)!!
        assertEquals(100.0,s.ema50!!,1e-8)
        assertEquals(0.0,s.macd!!.histogram!!,1e-8)
        assertEquals(2.0,s.atr14!!,1e-8)
        assertNull(IndicatorSnapshotEngine.calculate(h,h.observedAt+60000L))
    }
    @Test fun mismatchedIdentityIsMissing() {
        val h = history(60)
        val s = MultiTimeframeIndicatorEngine.calculate("BTCUSDT",mapOf(Timeframe.M1 to h, Timeframe.M5 to h),h.observedAt)
        assertEquals(setOf(Timeframe.M1),s.frames.keys)
        assertEquals(14,s.missing.size)
        assertTrue(MultiTimeframeIndicatorEngine.calculate("ETHUSDT",mapOf(Timeframe.M1 to h),h.observedAt).frames.isEmpty())
    }
}
