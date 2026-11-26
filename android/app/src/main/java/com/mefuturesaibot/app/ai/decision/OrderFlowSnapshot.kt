package com.mefuturesaibot.app.ai.decision

import com.mefuturesaibot.app.ai.Timeframe
import com.mefuturesaibot.app.ai.trend.TrendContext

internal data class OrderFlowSnapshot(
    val symbol: String,
    val timeframe: Timeframe,
    val closedAtMs: Long,
    val totalQuoteVolume: Double,
    val takerBuyQuoteVolume: Double
) {
    fun direction(context: TrendContext): EvidenceDirection {
        val snapshot = context.indicators
        if (symbol != snapshot.symbol || timeframe != snapshot.timeframe ||
            closedAtMs != snapshot.closedAtMs) return EvidenceDirection.MISSING
        return OrderFlowEvidenceEngine.direction(context.relativeVolume,totalQuoteVolume,takerBuyQuoteVolume)
    }
}
