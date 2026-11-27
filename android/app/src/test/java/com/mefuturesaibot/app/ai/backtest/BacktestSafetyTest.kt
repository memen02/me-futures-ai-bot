package com.mefuturesaibot.app.ai.backtest

import org.junit.Test
import org.junit.Assert.*

class BacktestSafetyTest {
    private fun bar(t: Long) = HistoricalBar(t,100.0,110.0,90.0,100.0,1.0)
    @Test fun closedHistoryAndStopFirst() {
        val data = HistoricalMarketData("BTCUSDT",60000L,listOf(bar(0),bar(60000)))
        assertEquals(1,data.closedAt(60000).size)
        val trades = BacktestSimulationEngine.run(data,0.001,0.0) {
            assertEquals(1,it.size)
            BacktestSignal(BacktestSide.LONG,95.0,105.0)
        }
        assertEquals(95.0,trades.single().exit,1e-9)
        assertEquals(-0.05195,trades.single().netReturn,1e-9)
    }
    @Test fun rejectsGapAndInvalidPrices() {
        assertFalse(HistoricalDataIntegrity.valid(listOf(bar(0),bar(120000)),60000))
        assertFalse(HistoricalDataIntegrity.valid(listOf(bar(0),bar(60000).copy(close=Double.NaN)),60000))
    }
    @Test fun fundingDebitsWithoutUncertainCredits() {
        val trade = BacktestTrade(0,60000,BacktestSide.LONG,100.0,100.0,0.0)
        val events = listOf(FundingObservation(0,0.001,100.0),FundingObservation(60000,-0.002,100.0))
        assertEquals(-0.001,BacktestTradingCosts.adjustedReturn(trade,events),1e-9)
        assertEquals(-0.002,BacktestTradingCosts.adjustedReturn(trade.copy(side=BacktestSide.SHORT),events),1e-9)
    }
    @Test fun capitalAndDrawdown() {
        val outcomes = BacktestCapitalSimulation.run(100.0,0.01,20,listOf(CapitalTrade(0.05,-0.05),CapitalTrade(0.05,0.10)))
        assertEquals(99.0,outcomes[0].after,1e-9)
        assertEquals(100.98,outcomes[1].after,1e-9)
        val report = BacktestPerformanceAnalysis.analyze(100.0,outcomes)
        assertEquals(0.5,report.winRate!!,1e-9)
        assertEquals(0.01,report.maxClosedEquityDrawdown,1e-9)
        assertEquals(1.98,report.profitFactor!!,1e-9)
    }
}
