package com.mefuturesaibot.app.ai.decision

import com.mefuturesaibot.app.ai.trend.TrendContext
import com.mefuturesaibot.app.ai.trend.TrendDirection
import com.mefuturesaibot.app.ai.trend.TrendStrength

internal object TrendEvidenceEngine {
    fun direction(context: TrendContext): EvidenceDirection {
        val trend = context.trend
        if (trend.direction == TrendDirection.UNKNOWN || trend.strength == TrendStrength.UNKNOWN)
            return EvidenceDirection.MISSING
        if (trend.strength == TrendStrength.WEAK) return EvidenceDirection.NEUTRAL
        return when (trend.direction) {
            TrendDirection.UP -> EvidenceDirection.LONG
            TrendDirection.DOWN -> EvidenceDirection.SHORT
            else -> EvidenceDirection.NEUTRAL
        }
    }
}
