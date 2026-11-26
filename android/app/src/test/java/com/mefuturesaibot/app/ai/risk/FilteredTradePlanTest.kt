package com.mefuturesaibot.app.ai.risk

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class FilteredTradePlanTest {
    private fun d(s: String) = BigDecimal(s)
    private fun plan(side: TradeSide, target: Double, cap: Int = 20) =
        FilteredTradePlanEngine.calculate(
            EntryCandidate(side,100.03),if (side == TradeSide.LONG) 99.0 else 101.0,
            1.0,target,0.1,0.1,100.0,20.0,
            d("0.1"),d("0.1"),d("1000"),d("0.01"),d("0.01"),d("10"),d("5"),cap,RiskPolicy())

    @Test fun longPlanRoundsConservatively() {
        val p = plan(TradeSide.LONG,105.09)!!
        assertEquals(100.1,p.entry.price,0.000001)
        assertEquals(98.5,p.stop,0.000001)
        assertEquals(105.0,p.target,0.000001)
        assertTrue(p.quantity.toDouble()*p.economics.lossPerUnit <= 0.5)
        assertTrue(p.economics.rewardRisk >= 2.0)
    }

    @Test fun shortPlanRoundsConservatively() {
        val p = plan(TradeSide.SHORT,94.91)!!
        assertEquals(100.0,p.entry.price,0.000001)
        assertEquals(101.5,p.stop,0.000001)
        assertEquals(95.0,p.target,0.000001)
        assertTrue(p.quantity.toDouble()*p.economics.lossPerUnit <= 0.5)
        assertTrue(p.economics.rewardRisk >= 2.0)
    }

    @Test fun rejectsPoorRewardAndInvalidCap() {
        assertNull(plan(TradeSide.LONG,103.0))
        assertNull(plan(TradeSide.SHORT,95.0,0))
    }
}
