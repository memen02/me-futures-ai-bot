package com.mefuturesaibot.app.ai.decision

internal data class LiquiditySnapshot(
    val symbol: String,
    val observedAtMs: Long,
    val bidNotional: Double,
    val askNotional: Double,
    val synchronized: Boolean
) {
    fun direction(expectedSymbol: String, nowMs: Long): EvidenceDirection {
        if (symbol.isBlank() || symbol != expectedSymbol || observedAtMs <= 0 ||
            nowMs < observedAtMs || nowMs - observedAtMs >= MAX_AGE_MS)
            return EvidenceDirection.MISSING
        return LiquidityEvidenceEngine.direction(bidNotional,askNotional,synchronized)
    }
    companion object {
        const val MAX_AGE_MS = 2000L
    }
}
