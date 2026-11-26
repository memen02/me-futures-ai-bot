package com.mefuturesaibot.app.ai.trend

import com.mefuturesaibot.app.ai.Timeframe
import com.mefuturesaibot.app.ai.TimeframeHistory
import com.mefuturesaibot.app.exchange.Candle
import org.junit.Test
import org.junit.Assert.*

class MarketStructureEngineTest {
    @Test fun pivotRequiresClosedRightNeighbor() {
        val candles = listOf(100,110,100).mapIndexed { i,p ->
            val v = p.toBigDecimal()
            Candle(i*60000L,v,v,v,v,1.toBigDecimal())
        }
        val h = TimeframeHistory("BTCUSDT",Timeframe.M1,candles,180000L)
        val early = h.copy(candles=candles.take(2),observedAt=120000L)
        assertTrue(MarketStructureEngine.swings(early,120000L,1)!!.isEmpty())
        val pivot = MarketStructureEngine.swings(h,180000L,1)!!.single()
        assertEquals(SwingKind.HIGH,pivot.kind)
        assertEquals(180000L,pivot.confirmedAt)
        assertNull(MarketStructureEngine.swings(h,240000L,1))
    }
    @Test fun higherHighAndHigherLowRequireConfirmation() {
        val points = listOf(
            SwingPoint(SwingKind.HIGH,1L,110.toBigDecimal(),2L),
            SwingPoint(SwingKind.LOW,3L,90.toBigDecimal(),4L),
            SwingPoint(SwingKind.HIGH,5L,120.toBigDecimal(),6L),
            SwingPoint(SwingKind.LOW,7L,100.toBigDecimal(),8L)
        )
        assertEquals(TrendDirection.UNKNOWN,StructureTrendEngine.assess(points,7L).direction)
        val result = StructureTrendEngine.assess(points,8L)
        assertEquals(HighStructure.HH,result.high)
        assertEquals(LowStructure.HL,result.low)
        assertEquals(TrendDirection.UP,result.direction)
    }
}
