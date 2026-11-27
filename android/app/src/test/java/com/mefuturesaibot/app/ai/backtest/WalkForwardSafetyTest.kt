package com.mefuturesaibot.app.ai.backtest

import org.junit.Test
import org.junit.Assert.*

class WalkForwardSafetyTest {
    @Test fun chronologicalDisjointEvaluation() {
        val data = HistoricalMarketData("BTCUSDT",60000L,(0..19).map {
            HistoricalBar(it*60000L,100.0,101.0,99.0,100.0,1.0)
        })
        val folds = WalkForwardValidation.folds(data,5,3,2)
        assertEquals(4,folds.size)
        for (fold in folds) {
            assertEquals(180000L,fold.evaluation.first().openTime-fold.training.last().openTime)
        }
        val times = folds.flatMap { it.evaluation.map { bar -> bar.openTime } }
        assertEquals(times.size,times.distinct().size)
    }
    @Test fun evaluationRejectsInvalidOrWeakReports() {
        val report = BacktestPerformance(100,80,20,0,0.8,2.0,10.0,0.05)
        assertEquals(ResearchVerdict.RESEARCH_CANDIDATE,BacktestStrategyEvaluation.assess(report))
        assertEquals(ResearchVerdict.INSUFFICIENT,BacktestStrategyEvaluation.assess(report.copy(winRate=0.9)))
        assertEquals(ResearchVerdict.BELOW_TARGET,BacktestStrategyEvaluation.assess(report.copy(netPnl=-1.0)))
        assertEquals(ResearchVerdict.BELOW_TARGET,BacktestStrategyEvaluation.assess(report.copy(maxClosedEquityDrawdown=0.2)))
    }
}
