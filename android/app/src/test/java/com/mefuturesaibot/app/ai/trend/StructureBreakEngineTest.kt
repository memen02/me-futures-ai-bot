package com.mefuturesaibot.app.ai.trend

import com.mefuturesaibot.app.ai.Timeframe
import com.mefuturesaibot.app.ai.TimeframeHistory
import com.mefuturesaibot.app.exchange.Candle
import org.junit.Test
import org.junit.Assert.*

class StructureBreakEngineTest {
    private fun history(close: Int, high: Int = close): TimeframeHistory {
        val candles = listOf(100,110,100).mapIndexed { i,p ->
            val v = p.toBigDecimal()
            Candle(i*60000L,v,v,v,v,1.toBigDecimal())
        } + Candle(180000L,100.toBigDecimal(),high.toBigDecimal(),100.toBigDecimal(),close.toBigDecimal(),1.toBigDecimal())
        return TimeframeHistory("BTCUSDT",Timeframe.M1,candles,240000L)
    }
    @Test fun closeBeyondConfirmedHighIsBreak() {
        val h = history(115)
        val result = StructureBreakEngine.detect(h,240000L,1)!!
        assertEquals(StructureBreakKind.UNCLASSIFIED_UP,result.kind)
        assertEquals(110.toBigDecimal(),result.level)
        assertNull(LiquiditySweepEngine.detect(h,240000L,1))
    }
    @Test fun wickAloneIsNotBreak() {
        val h = history(105,115)
        assertNull(StructureBreakEngine.detect(h,240000L,1))
        assertEquals(110.toBigDecimal(),LiquiditySweepEngine.detect(h,240000L,1)!!.upperLevel)
        assertNull(LiquiditySweepEngine.detect(h,300000L,1))
    }
}
