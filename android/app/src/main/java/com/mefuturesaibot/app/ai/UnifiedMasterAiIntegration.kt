package com.mefuturesaibot.app.ai

import com.mefuturesaibot.app.ai.decision.*
import com.mefuturesaibot.app.ai.trend.*
import java.util.Collections

internal data class UnifiedMasterAssessment(val decision: MasterDecision,
    val evidence: List<DecisionEvidence>, val evaluatedAtMs: Long)

// Research signal only. Compute context from the captured source, not caller-supplied votes.
internal object UnifiedMasterAiIntegration {
    fun evaluate(source: UnifiedTimeframeSnapshot, primary: Timeframe, required: Set<Timeframe>,
        flows: Map<Timeframe, OrderFlowSnapshot>, book: LiquiditySnapshot?,
        earliest: Long, latest: Long): UnifiedMasterAssessment {
        fun wait(reason: DecisionReason) = UnifiedMasterAssessment(
            MasterDecision(MasterDirection.WAIT,reason,0),emptyList(),latest)
        val frames = required.toSet()
        if (frames.size < 2 || primary !in frames || !source.fresh(earliest,latest) ||
            !source.frames.keys.containsAll(frames)) return wait(DecisionReason.INVALID_EVIDENCE)
        val trend = UnifiedTrendIntegration.calculate(source) ?: return wait(DecisionReason.INVALID_EVIDENCE)
        val value = trend.frames[primary] ?: return wait(DecisionReason.INVALID_EVIDENCE)
        if (!trend.frames.keys.containsAll(frames)) return wait(DecisionReason.INSUFFICIENT_EVIDENCE)
        if (frames.any { MasterGuardian.blocks(trend.frames.getValue(it)) }) return wait(DecisionReason.VETO)
        val regime = UnifiedMarketRegimeIntegration.calculate(source,trend,earliest,latest)[primary]?.regime
        if (regime != UnifiedRegime.UPTREND && regime != UnifiedRegime.DOWNTREND) return wait(DecisionReason.VETO)
        val directions = frames.map { TrendEvidenceEngine.direction(trend.frames.getValue(it)) }
        val alignment = when {
            directions.all { it == EvidenceDirection.LONG } -> EvidenceDirection.LONG
            directions.all { it == EvidenceDirection.SHORT } -> EvidenceDirection.SHORT
            else -> EvidenceDirection.NEUTRAL
        }
        val flow = UnifiedOrderFlowIntegration.calculate(source,trend,flows,earliest,latest)[primary]
            ?: EvidenceDirection.MISSING
        val liquidity = UnifiedLiquidityIntegration.direction(source.symbol,book,earliest,latest)
        val end = value.indicators.closedAtMs + 1L
        val expiry = frames.minOf { frame ->
            frame.nextOpen(frame.nextOpen(requireNotNull(source.frames.getValue(frame).lastOpen)))
        }
        val votes = listOf(
            EvidenceGroup.TREND to TrendEvidenceEngine.direction(value),
            EvidenceGroup.MOMENTUM to MomentumEvidenceEngine.direction(value.indicators),
            EvidenceGroup.STRUCTURE to StructureEvidenceEngine.direction(value),
            EvidenceGroup.VOLUME_ORDER_FLOW to flow,
            EvidenceGroup.CROSS_TIMEFRAME to alignment
        ).map { (group,direction) -> DecisionEvidence(group,direction,end,expiry) }.toMutableList()
        val bookExpiry = if (liquidity == EvidenceDirection.MISSING || book == null) expiry
            else minOf(expiry,book.observedAtMs.coerceAtMost(Long.MAX_VALUE-LiquiditySnapshot.MAX_AGE_MS)+LiquiditySnapshot.MAX_AGE_MS)
        votes.add(DecisionEvidence(EvidenceGroup.LIQUIDITY,liquidity,
            if (liquidity == EvidenceDirection.MISSING || book == null) end else book.observedAtMs,bookExpiry))
        return UnifiedMasterAssessment(MasterDecisionEngine.evaluate(votes,latest,false),
            Collections.unmodifiableList(votes),latest)
    }
}
