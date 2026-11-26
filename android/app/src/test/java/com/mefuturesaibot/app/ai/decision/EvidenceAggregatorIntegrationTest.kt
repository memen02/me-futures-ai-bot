package com.mefuturesaibot.app.ai.decision

import com.mefuturesaibot.app.ai.Timeframe
import com.mefuturesaibot.app.ai.indicators.*
import com.mefuturesaibot.app.ai.trend.*
import org.junit.Test
import org.junit.Assert.*

class EvidenceAggregatorIntegrationTest {
    private val now = 300000L
    private val required = setOf(Timeframe.M1,Timeframe.M5)
    private fun frame(tf: Timeframe) = TrendContext(
        IndicatorSnapshot("BTCUSDT",tf,now-1,60,110.0,MacdPoint(2.0,1.0,1.0),
            105.0,100.0,60.0,null,2.0,null,1.0,null,null,DirectionalStrength(30.0,25.0,10.0)),
        TrendAssessment(TrendDirection.UP,TrendStrength.STRONG),
        StructureTrend(HighStructure.HH,LowStructure.HL,TrendDirection.UP),
        null,null,null,1.5,VolatilityRegime.NORMAL,TrendEvent.WAIT
    )
    private val context = MultiTimeframeTrendContext("BTCUSDT",now,required.associateWith { frame(it) })
    private val flow = OrderFlowSnapshot("BTCUSDT",Timeframe.M1,now-1,100.0,70.0)
    private val book = LiquiditySnapshot("BTCUSDT",now,80.0,20.0,true)
    private fun evaluate(c: MultiTimeframeTrendContext = context, f: OrderFlowSnapshot? = flow, l: LiquiditySnapshot? = book) =
        EvidenceAggregator.evaluate(c,Timeframe.M1,required,now,f,l)

    @Test fun allValidEvidenceCanAgree() {
        assertEquals(MasterDirection.LONG,evaluate().direction)
        assertEquals(5,evaluate().confirmations)
    }
    @Test fun missingOrMismatchedExternalEvidenceBlocks() {
        assertEquals(MasterDirection.WAIT,evaluate(f=null).direction)
        assertEquals(MasterDirection.WAIT,evaluate(f=flow.copy(symbol="ETHUSDT")).direction)
        assertEquals(MasterDirection.WAIT,evaluate(f=flow.copy(closedAtMs=now-60001)).direction)
        assertEquals(MasterDirection.WAIT,evaluate(l=book.copy(observedAtMs=now-2000)).direction)
        assertEquals(MasterDirection.WAIT,evaluate(l=book.copy(synchronized=false)).direction)
    }
    @Test fun guardianOverridesConsensus() {
        val blocked = context.copy(frames=context.frames + (Timeframe.M1 to frame(Timeframe.M1).copy(volatility=VolatilityRegime.EXPANDED)))
        assertEquals(DecisionReason.VETO,evaluate(c=blocked).reason)
    }
}
