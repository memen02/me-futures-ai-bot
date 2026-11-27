package com.mefuturesaibot.app.ai.backtest

import com.mefuturesaibot.app.ai.risk.RiskPolicy
import org.junit.Test
import org.junit.Assert.*

class BacktestReleaseSafetyTest {
    @Test fun chronologicalTrainingAndEvaluation() {
        val data = HistoricalMarketData("BTCUSDT",60000L,(0..19).map {
            HistoricalBar(it*60000L,100.0,101.0,99.0,100.0,1.0)
        })
        var trained = 0
        val folds = WalkForwardBacktestCoordinator.run(data,5,3,1,100.0,
            RiskPolicy(),0.0,0.0,0.0,emptyList()) { training ->
            trained++
            val last = training.last().openTime
            val strategy: (List<HistoricalBar>) -> BacktestSignal? = { history ->
                assertTrue(history.first().openTime > last+60000L)
                assertTrue(history.size in 1..2)
                null
            }
            strategy
        }
        assertEquals(4,trained)
        assertEquals(4,folds.size)
        assertTrue(folds.all { it.result.executedTrades == 0 })
        assertEquals(BacktestReleaseDecision.BLOCKED,BacktestReleaseSafetyGuardian.assess(folds))
    }
    @Test fun strongReportsNeverAuthorizeReal() {
        val performance = BacktestPerformance(100,80,20,0,0.8,2.0,10.0,0.05)
        val result = UnifiedBacktestResult(100,100,performance)
        val folds = (0..2).map {
            WalkForwardResearchResult(1L+it*1000,2L+it*1000,1001L+it*1000,result)
        }
        assertEquals(BacktestReleaseDecision.RESEARCH_REVIEW_ONLY,BacktestReleaseSafetyGuardian.assess(folds))
        assertEquals(BacktestReleaseDecision.BLOCKED,BacktestReleaseSafetyGuardian.assess(folds.take(2)))
        assertEquals(BacktestReleaseDecision.BLOCKED,BacktestReleaseSafetyGuardian.assess(listOf(folds[0],folds[0],folds[2])))
        assertEquals(BacktestReleaseDecision.BLOCKED,BacktestReleaseSafetyGuardian.assess(
            folds.map { it.copy(result=result.copy(simulatedTrades=101)) }))
    }
}
