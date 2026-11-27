package com.mefuturesaibot.app.ai

import com.mefuturesaibot.app.ai.decision.*

// Group separation is not statistical independence. Never interpret count as probability.
internal object IndependentConfirmationIntegration {
    fun revalidate(assessment: UnifiedMasterAssessment, earliest: Long, latest: Long): MasterDecision {
        val wait = MasterDecision(MasterDirection.WAIT,DecisionReason.INVALID_EVIDENCE,0)
        if (earliest <= 0 || latest < earliest || earliest < assessment.evaluatedAtMs ||
            assessment.decision.direction == MasterDirection.WAIT) return wait
        val votes = assessment.evidence.toList()
        if (votes.size != EvidenceGroup.entries.size ||
            votes.map { it.group }.toSet() != EvidenceGroup.entries.toSet()) return wait
        val first = MasterDecisionEngine.evaluate(votes,earliest,false)
        val last = MasterDecisionEngine.evaluate(votes,latest,false)
        return if (first == last && last == assessment.decision && last.confirmations == 5) last else wait
    }
}
