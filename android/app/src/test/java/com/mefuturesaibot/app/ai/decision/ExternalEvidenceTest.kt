package com.mefuturesaibot.app.ai.decision

import com.mefuturesaibot.app.ai.Timeframe
import com.mefuturesaibot.app.ai.trend.MultiTimeframeTrendContext
import org.junit.Test
import org.junit.Assert.*

class ExternalEvidenceTest {
    @Test fun orderFlowRequiresValidDirectionalVolume() {
        assertEquals(EvidenceDirection.LONG,OrderFlowEvidenceEngine.direction(1.5,100.0,70.0))
        assertEquals(EvidenceDirection.SHORT,OrderFlowEvidenceEngine.direction(1.5,100.0,30.0))
        assertEquals(EvidenceDirection.NEUTRAL,OrderFlowEvidenceEngine.direction(1.0,100.0,70.0))
        assertEquals(EvidenceDirection.MISSING,OrderFlowEvidenceEngine.direction(1.5,100.0,101.0))
        assertEquals(EvidenceDirection.MISSING,OrderFlowEvidenceEngine.direction(1.5,100.0,null))
    }
    @Test fun liquidityRequiresSynchronizedBook() {
        assertEquals(EvidenceDirection.LONG,LiquidityEvidenceEngine.direction(80.0,20.0,true))
        assertEquals(EvidenceDirection.SHORT,LiquidityEvidenceEngine.direction(20.0,80.0,true))
        assertEquals(EvidenceDirection.MISSING,LiquidityEvidenceEngine.direction(80.0,20.0,false))
        assertEquals(EvidenceDirection.MISSING,LiquidityEvidenceEngine.direction(Double.NaN,20.0,true))
    }
    @Test fun missingContextCannotConfirmEntry() {
        val context = MultiTimeframeTrendContext("BTCUSDT",60000L,emptyMap())
        assertEquals(MasterDirection.WAIT,EvidenceAggregator.evaluate(context,Timeframe.M1,setOf(Timeframe.M1,Timeframe.M5),60000L).direction)
    }
}
