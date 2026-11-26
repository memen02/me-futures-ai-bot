package com.mefuturesaibot.app.ai.risk

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class UnifiedTradePlanTest {
    private fun d(value: String) = BigDecimal(value)
    private fun plan(target: Double = 105.0) = UnifiedTradePlanEngine.calculate(
        EntryCandidate(TradeSide.LONG,100.0),99.0,1.0,target,0.1,0.1,
        100.0,20.0,d("0.01"),d("0.01"),d("10"),d("5"),20,RiskPolicy())

    @Test fun integratedPlanRespectsRisk() {
        val p = plan()!!
        assertEquals(98.5,p.stop,0.0)
        assertEquals(0,d("0.31").compareTo(p.quantity))
        assertEquals(2,p.leverage)
        assertTrue(FinalRiskGuardian.permits(p,100.0,0.0,0.0,95.0,RiskPolicy()))
        assertFalse(FinalRiskGuardian.permits(p,100.0,0.0,0.0,null,RiskPolicy()))
        assertFalse(FinalRiskGuardian.permits(p,100.0,1.9,0.0,95.0,RiskPolicy()))
        assertNull(plan(102.0))
    }

    @Test fun priceRoundingRespectsBounds() {
        assertEquals(0,d("100.1").compareTo(ExchangePriceFilter.round(d("100.01"),d("0.1"),d("1"),d("200"),true)!!))
        assertEquals(0,d("100.0").compareTo(ExchangePriceFilter.round(d("100.09"),d("0.1"),d("1"),d("200"),false)!!))
        assertNull(ExchangePriceFilter.round(d("200.01"),d("0.1"),d("1"),d("200"),true))
    }

    @Test fun expiredAndFuturePlansAreRejected() {
        assertTrue(TradePlanFreshness.valid(1000,900,3000,1999))
        assertFalse(TradePlanFreshness.valid(1000,900,3000,2000))
        assertFalse(TradePlanFreshness.valid(1000,900,1500,1500))
        assertFalse(TradePlanFreshness.valid(1000,1100,3000,1200))
    }
}
