package com.mefuturesaibot.app.ai.trend

import com.mefuturesaibot.app.ai.Timeframe
import com.mefuturesaibot.app.ai.indicators.*
import org.junit.Test
import org.junit.Assert.*

class TrendEngineTest {
    private fun snapshot(frame: Timeframe = Timeframe.M1, up: Boolean = true) = IndicatorSnapshot(
        symbol = "BTCUSDT", timeframe = frame, closedAtMs = 59999L, candleCount = 60,
        close = 100.0, macd = null, ema20 = if (up) 101.0 else 99.0, ema50 = 100.0,
        rsi14 = null, bollinger = null, roc12 = null, wma20 = null, atr14 = null,
        stochasticRsi = null, rollingVwap = null,
        directionalStrength = DirectionalStrength(30.0, if (up) 30.0 else 10.0, if (up) 10.0 else 30.0)
    )
    @Test fun directionalAgreement() {
        assertEquals(TrendDirection.UP, TrendEngine.assess(snapshot()).direction)
        assertEquals(TrendDirection.DOWN, TrendEngine.assess(snapshot(up = false)).direction)
        assertEquals(TrendStrength.STRONG, TrendEngine.assess(snapshot()).strength)
    }
    @Test fun missingAndConflictingInputsAreUnknown() {
        assertEquals(TrendDirection.UNKNOWN, TrendEngine.assess(snapshot().copy(ema50 = null)).direction)
        assertEquals(TrendDirection.UNKNOWN, TrendEngine.assess(snapshot().copy(ema20 = 99.0)).direction)
    }
    @Test fun lowAdxIsSidewaysCandidate() {
        assertEquals(TrendDirection.SIDEWAYS, TrendEngine.assess(snapshot().copy(directionalStrength = DirectionalStrength(10.0, 15.0, 10.0))).direction)
    }
    @Test fun alignmentRejectsMissingAndOpposingFrames() {
        val s = MultiTimeframeIndicators("BTCUSDT", 60000L, mapOf(Timeframe.M1 to snapshot(), Timeframe.M5 to snapshot(Timeframe.M5, false)))
        assertEquals(Alignment.CONFLICT, TrendAlignment.assess(s, setOf(Timeframe.M1, Timeframe.M5)))
        assertEquals(Alignment.UNRESOLVED, TrendAlignment.assess(s, setOf(Timeframe.H1)))
        assertEquals(Alignment.ALIGNED_UP, TrendAlignment.assess(s, setOf(Timeframe.M1)))
    }
}
