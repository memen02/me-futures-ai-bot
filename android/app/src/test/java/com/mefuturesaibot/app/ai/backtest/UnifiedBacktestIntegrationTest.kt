package com.mefuturesaibot.app.ai.backtest

import com.mefuturesaibot.app.ai.risk.RiskPolicy
import org.junit.Test
import org.junit.Assert.*

class UnifiedBacktestIntegrationTest {
    private val data = HistoricalMarketData("BTCUSDT",60000L,listOf(
        HistoricalBar(0,100.0,101.0,99.0,100.0,1.0),
        HistoricalBar(60000,100.0,116.0,99.0,115.0,1.0)))
    @Test fun integratesRiskCostsAndCapital() {
        val result = UnifiedBacktestCoordinator.run(data,100.0,RiskPolicy(0.01),0.0,0.0,0.0,
            listOf(FundingObservation(60000,0.001,100.0))) {
            assertEquals(1,it.size)
            BacktestSignal(BacktestSide.LONG,95.0,115.0)
        }
        assertEquals(1,result.executedTrades)
        assertEquals(2.98,result.performance.netPnl,1e-9)
        assertEquals(1,result.performance.wins)
        assertTrue(BacktestReportEngine.create(data,"v1",result).researchOnly)
    }
    @Test fun rejectsWeakRiskRewardBeforeExecution() {
        val result = UnifiedBacktestCoordinator.run(data,100.0,RiskPolicy(),0.001,0.0,0.0,emptyList()) {
            BacktestSignal(BacktestSide.LONG,95.0,105.0)
        }
        assertEquals(0,result.executedTrades)
        assertNull(result.performance.winRate)
    }
}
