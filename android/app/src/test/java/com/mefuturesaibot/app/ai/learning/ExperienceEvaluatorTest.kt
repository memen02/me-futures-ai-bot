package com.mefuturesaibot.app.ai.learning

import org.junit.Assert.*
import org.junit.Test

class ExperienceEvaluatorTest {
    private val trade = TradeExperience("a","demo","v1","BTCUSDT",1000,2000,2.0,0.2,0.1,1.0)
    @Test fun evaluatesNetResults() {
        val loss = trade.copy(tradeId="b",grossPnl=0.1)
        val stats = ExperienceEvaluator.evaluate(listOf(trade,loss),"demo","v1",2000)!!
        assertEquals(2,stats.count)
        assertEquals(1,stats.wins)
        assertEquals(1,stats.losses)
        assertEquals(0.5,stats.winRate,0.000001)
        assertEquals(0.75,stats.meanNetR,0.000001)
    }
    @Test fun rejectsDuplicatesFutureAndMixedDatasets() {
        assertNull(ExperienceEvaluator.evaluate(listOf(trade,trade),"demo","v1",2000))
        assertNull(ExperienceEvaluator.evaluate(listOf(trade),"demo","v1",1999))
        assertNull(ExperienceEvaluator.evaluate(listOf(trade),"real","v1",2000))
        assertNull(ExperienceEvaluator.evaluate(listOf(trade),"demo","v2",2000))
        assertNull(ExperienceEvaluator.evaluate(listOf(trade.copy(initialRisk=0.0)),"demo","v1",2000))
    }
}
