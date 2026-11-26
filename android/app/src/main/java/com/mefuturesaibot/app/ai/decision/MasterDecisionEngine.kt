package com.mefuturesaibot.app.ai.decision

internal object MasterDecisionEngine {
    // A repeated indicator or timeframe cannot increase the group count.
    // Cross-timeframe agreement is a guard, not an independent directional vote.
    fun evaluate(evidence: List<DecisionEvidence>, nowMs: Long, veto: Boolean): MasterDecision {
        fun wait(reason: DecisionReason, count: Int = 0) = MasterDecision(MasterDirection.WAIT,reason,count)
        if (veto) return wait(DecisionReason.VETO)
        if (nowMs <= 0 || evidence.size > EvidenceGroup.entries.size ||
            evidence.map { it.group }.distinct().size != evidence.size ||
            evidence.any { !it.isFresh(nowMs) }) return wait(DecisionReason.INVALID_EVIDENCE)
        val votes = evidence.filter { it.group != EvidenceGroup.CROSS_TIMEFRAME }
        val longs = votes.count { it.direction == EvidenceDirection.LONG }
        val shorts = votes.count { it.direction == EvidenceDirection.SHORT }
        if (longs > 0 && shorts > 0) return wait(DecisionReason.CONFLICT)
        val count = maxOf(longs,shorts)
        if (count < 5) return wait(DecisionReason.INSUFFICIENT_EVIDENCE,count)
        val alignment = evidence.firstOrNull { it.group == EvidenceGroup.CROSS_TIMEFRAME }?.direction
        val expected = if (longs == 5) EvidenceDirection.LONG else EvidenceDirection.SHORT
        if (alignment == null || alignment == EvidenceDirection.MISSING || alignment == EvidenceDirection.NEUTRAL)
            return wait(DecisionReason.INSUFFICIENT_EVIDENCE,count)
        if (alignment != expected) return wait(DecisionReason.CONFLICT,count)
        return MasterDecision(if (expected == EvidenceDirection.LONG) MasterDirection.LONG else MasterDirection.SHORT,
            DecisionReason.AGREEMENT,count)
    }
}
