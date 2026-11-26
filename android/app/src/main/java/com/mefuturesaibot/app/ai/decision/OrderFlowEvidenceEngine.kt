package com.mefuturesaibot.app.ai.decision

internal object OrderFlowEvidenceEngine {
    // Inputs must represent the same closed interval and symbol.
    fun direction(relativeVolume: Double?, totalQuoteVolume: Double?, takerBuyQuoteVolume: Double?): EvidenceDirection {
        val relative = relativeVolume ?: return EvidenceDirection.MISSING
        val total = totalQuoteVolume ?: return EvidenceDirection.MISSING
        val buy = takerBuyQuoteVolume ?: return EvidenceDirection.MISSING
        if (!relative.isFinite() || relative < 0 || !total.isFinite() || total <= 0 ||
            !buy.isFinite() || buy < 0 || buy > total) return EvidenceDirection.MISSING
        if (relative < 1.2) return EvidenceDirection.NEUTRAL
        val fraction = buy / total
        return when {
            fraction >= 0.60 -> EvidenceDirection.LONG
            fraction <= 0.40 -> EvidenceDirection.SHORT
            else -> EvidenceDirection.NEUTRAL
        }
    }
}
