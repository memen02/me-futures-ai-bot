package com.mefuturesaibot.app.ai.backtest

import com.mefuturesaibot.app.ai.risk.RiskPolicy
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoricalRiskIntegrationTest {
    private val payload = """[[0,"100","110","90","100","1",59999,"0",1,"0","0","0"],[60000,"100","110","90","100","1",119999,"0",1,"0","0","0"]]"""
    @Test fun closedCandlesOnly() {
        assertEquals(2,HistoricalMarketDataIntegration.parse("BTCUSDT",60000,payload,120000).bars.size)
        try {
            HistoricalMarketDataIntegration.parse("BTCUSDT",60000,payload,119999)
            fail("Unclosed candle accepted")
        } catch (_: IllegalArgumentException) {}
    }
    @Test fun netRewardRiskAndPreEntryFilter() {
        val policy = RiskPolicy()
        assertNull(BacktestRiskIntegration.plannedRisk(BacktestSide.LONG,100.0,95.0,110.0,0.001,0.0,0.0,policy))
        assertEquals(0.05,BacktestRiskIntegration.plannedRisk(BacktestSide.LONG,100.0,95.0,115.0,0.0,0.0,0.0,policy)!!,1e-9)
        val data = HistoricalMarketDataIntegration.parse("BTCUSDT",60000,payload,120000)
        val trades = BacktestSimulationEngine.runFiltered(data,0.0,0.0,{ _,_ -> null }) {
            BacktestSignal(BacktestSide.LONG,95.0,115.0)
        }
        assertTrue(trades.isEmpty())
    }
}
