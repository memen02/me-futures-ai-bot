package com.mefuturesaibot.app.ai.decision

import com.mefuturesaibot.app.ai.Timeframe
import com.mefuturesaibot.app.ai.trend.MultiTimeframeTrendContext

internal object EvidenceAggregator {
    // External market evidence is deliberately missing until timestamped,
    // symbol-bound order flow and synchronized depth adapters are available.
    fun evaluate(context: MultiTimeframeTrendContext, primary: Timeframe,
                 required: Set<Timeframe>, nowMs: Long): MasterDecision {
        val wait = MasterDecision(MasterDirection.WAIT,DecisionReason.INVALID_EVIDENCE,0)
        if (context.evaluatedAtMs != nowMs || nowMs <= 0 || primary !in required) return wait
        val value = context.frames[primary] ?: return wait
        val snapshot = value.indicators
        if (snapshot.symbol != context.symbol || snapshot.timeframe != primary) return wait
        val end = try { Math.addExact(snapshot.closedAtMs,1L) } catch (_: ArithmeticException) { return wait }
        val expiry = try { primary.nextOpen(end) } catch (_: RuntimeException) { return wait }
        if (nowMs < end || nowMs >= expiry) return wait
        val votes = listOf(
            EvidenceGroup.TREND to TrendEvidenceEngine.direction(value),
            EvidenceGroup.MOMENTUM to MomentumEvidenceEngine.direction(snapshot),
            EvidenceGroup.STRUCTURE to StructureEvidenceEngine.direction(value),
            EvidenceGroup.VOLUME_ORDER_FLOW to EvidenceDirection.MISSING,
            EvidenceGroup.LIQUIDITY to EvidenceDirection.MISSING,
            EvidenceGroup.CROSS_TIMEFRAME to TimeframeEvidenceEngine.direction(context,required,nowMs)
        ).map { (group,direction) -> DecisionEvidence(group,direction,end,expiry) }
        return MasterDecisionEngine.evaluate(votes,nowMs,MasterGuardian.blocks(value))
    }
}
