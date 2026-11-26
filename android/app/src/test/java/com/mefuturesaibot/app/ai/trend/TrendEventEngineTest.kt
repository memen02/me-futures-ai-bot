package com.mefuturesaibot.app.ai.trend

import org.junit.Test
import org.junit.Assert.*

class TrendEventEngineTest {
    private val up = TrendAssessment(TrendDirection.UP,TrendStrength.STRONG)
    private val down = TrendAssessment(TrendDirection.DOWN,TrendStrength.STRONG)
    private val structure = StructureTrend(HighStructure.HH,LowStructure.HL,TrendDirection.UP)
    private fun event(kind: StructureBreakKind) = StructureBreak(kind,100.toBigDecimal(),59999L)
    @Test fun continuationRequiresTrendAgreement() {
        assertEquals(TrendEvent.CONTINUATION_UP,TrendEventEngine.assess(up,structure,event(StructureBreakKind.BOS_UP),null))
        assertEquals(TrendEvent.WAIT,TrendEventEngine.assess(down,structure,event(StructureBreakKind.BOS_UP),null))
    }
    @Test fun reversalIsOnlyCandidate() {
        assertEquals(TrendEvent.REVERSAL_CANDIDATE_DOWN,TrendEventEngine.assess(up,structure,event(StructureBreakKind.CHOCH_DOWN),null))
    }
    @Test fun conflictingEvidenceAndSweepDoNotConfirmEntry() {
        assertEquals(TrendEvent.CONFLICT,TrendEventEngine.assess(down,structure,null,null))
        assertEquals(TrendEvent.WAIT,TrendEventEngine.assess(up,structure,event(StructureBreakKind.BOS_UP),LiquiditySweep(100.toBigDecimal(),null,59999L)))
    }
}
