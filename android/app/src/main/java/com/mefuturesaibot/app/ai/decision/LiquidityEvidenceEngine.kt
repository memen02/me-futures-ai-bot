package com.mefuturesaibot.app.ai.decision

internal object LiquidityEvidenceEngine {
    // Depth quantities must be measured over symmetric price bands on a synchronized book.
    // Resting liquidity is not executed order flow and may be cancelled.
    fun direction(bidNotional: Double?, askNotional: Double?, synchronized: Boolean): EvidenceDirection {
        if (!synchronized) return EvidenceDirection.MISSING
        val bid = bidNotional ?: return EvidenceDirection.MISSING
        val ask = askNotional ?: return EvidenceDirection.MISSING
        if (!bid.isFinite() || !ask.isFinite() || bid <= 0 || ask <= 0) return EvidenceDirection.MISSING
        val scale = maxOf(bid,ask)
        val fraction = (bid/scale)/(bid/scale+ask/scale)
        return when {
            fraction >= 0.65 -> EvidenceDirection.LONG
            fraction <= 0.35 -> EvidenceDirection.SHORT
            else -> EvidenceDirection.NEUTRAL
        }
    }
}
