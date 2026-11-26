package com.mefuturesaibot.app.ai.risk

import java.math.BigDecimal
import org.junit.Test
import org.junit.Assert.*

class RiskManagementTest {
    @Test fun costsReduceRewardRiskAndPositionSize() {
        val economics = TradeEconomicsEngine.calculate(TradeSide.LONG,100.0,99.0,103.0,0.1,0.1)!!
        assertEquals(1.1,economics.lossPerUnit,0.000001)
        assertEquals(2.9,economics.rewardPerUnit,0.000001)
        assertEquals(0.5/1.1,PositionRiskSizer.maximumQuantity(100.0,20.0,100.0,economics,RiskPolicy())!!,0.000001)
        val poor = TradeEconomicsEngine.calculate(TradeSide.LONG,100.0,99.0,102.0,0.1,0.1)!!
        assertNull(PositionRiskSizer.maximumQuantity(100.0,20.0,100.0,poor,RiskPolicy()))
    }
    @Test fun trailingNeverWidensStop() {
        assertEquals(102.0,SmartTrailingStopEngine.tighten(TradeSide.LONG,100.0,99.0,104.0,1.0,1.0)!!,0.0)
        assertNull(SmartTrailingStopEngine.tighten(TradeSide.LONG,100.0,103.0,104.0,1.0,1.0))
        assertEquals(98.0,SmartTrailingStopEngine.tighten(TradeSide.SHORT,100.0,101.0,96.0,1.0,1.0)!!,0.0)
    }
    @Test fun leverageAndExposureAreBounded() {
        assertEquals(5,SmartLeverageEngine.select(100.0,20.0,20,RiskPolicy()))
        assertNull(SmartLeverageEngine.select(100.0,4.0,20,RiskPolicy()))
        assertFalse(ExposureGuard.permits(100.0,null,0.5,0.0))
        assertFalse(ExposureGuard.permits(100.0,1.8,0.5,0.0))
        assertTrue(ExposureGuard.permits(100.0,0.5,0.5,0.0))
    }
    @Test fun quantityRoundsDownAndRejectsBelowMinimum() {
        fun d(s: String) = BigDecimal(s)
        assertEquals(0,d("0.12").compareTo(ExchangeQuantityFilter.roundDown(d("0.129"),d("0.01"),d("0.01"),d("1"),d("100"),d("5"))!!))
        assertNull(ExchangeQuantityFilter.roundDown(d("0.029"),d("0.01"),d("0.01"),d("1"),d("100"),d("5")))
    }
    @Test fun liquidationRequiresKnownSafeDistance() {
        assertTrue(LiquidationProtection.permits(TradeSide.LONG,100.0,98.0,95.0))
        assertFalse(LiquidationProtection.permits(TradeSide.LONG,100.0,98.0,97.9))
        assertFalse(LiquidationProtection.permits(TradeSide.SHORT,100.0,102.0,null))
    }
}
