package com.mefuturesaibot.app.ai.decision

import org.junit.Test
import org.junit.Assert.*

class MasterDecisionEngineTest {
    private fun votes(direction: EvidenceDirection) = EvidenceGroup.entries.map {
        DecisionEvidence(it,direction,100L,200L)
    }
    @Test fun unanimousEvidenceSupportsBothDirections() {
        assertEquals(MasterDirection.LONG,MasterDecisionEngine.evaluate(votes(EvidenceDirection.LONG),150L,false).direction)
        assertEquals(MasterDirection.SHORT,MasterDecisionEngine.evaluate(votes(EvidenceDirection.SHORT),150L,false).direction)
        assertEquals(5,MasterDecisionEngine.evaluate(votes(EvidenceDirection.LONG),150L,false).confirmations)
    }
    @Test fun missingAlignmentOrGroupCannotConfirm() {
        val all = votes(EvidenceDirection.LONG)
        for (group in EvidenceGroup.entries) {
            assertEquals(MasterDirection.WAIT,MasterDecisionEngine.evaluate(all.filter { it.group != group },150L,false).direction)
        }
    }
    @Test fun duplicatesStaleConflictAndVetoBlockDecision() {
        val all = votes(EvidenceDirection.LONG)
        assertEquals(DecisionReason.INVALID_EVIDENCE,MasterDecisionEngine.evaluate(all+all.first(),150L,false).reason)
        assertEquals(DecisionReason.INVALID_EVIDENCE,MasterDecisionEngine.evaluate(all,200L,false).reason)
        assertEquals(DecisionReason.CONFLICT,MasterDecisionEngine.evaluate(all.map {
            if (it.group == EvidenceGroup.MOMENTUM) it.copy(direction=EvidenceDirection.SHORT) else it
        },150L,false).reason)
        assertEquals(DecisionReason.VETO,MasterDecisionEngine.evaluate(all,150L,true).reason)
    }
}
