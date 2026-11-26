package com.mefuturesaibot.app.ai.decision

internal enum class EvidenceGroup { TREND, MOMENTUM, STRUCTURE, VOLUME_ORDER_FLOW, LIQUIDITY, CROSS_TIMEFRAME }
internal enum class EvidenceDirection { LONG, SHORT, NEUTRAL, MISSING }
internal data class DecisionEvidence(
    val group: EvidenceGroup,
    val direction: EvidenceDirection,
    val observedAtMs: Long,
    val expiresAtMs: Long
) {
    fun isFresh(nowMs: Long): Boolean =
        observedAtMs > 0 && observedAtMs <= nowMs && nowMs < expiresAtMs
}
internal enum class MasterDirection { LONG, SHORT, WAIT }
internal enum class DecisionReason { AGREEMENT, INSUFFICIENT_EVIDENCE, CONFLICT, INVALID_EVIDENCE, VETO }
internal data class MasterDecision(
    val direction: MasterDirection,
    val reason: DecisionReason,
    val confirmations: Int
)
