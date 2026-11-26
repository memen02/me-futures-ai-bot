package com.mefuturesaibot.app.ai.decision

import com.mefuturesaibot.app.ai.trend.TrendContext
import com.mefuturesaibot.app.ai.trend.TrendDirection
import com.mefuturesaibot.app.ai.trend.TrendEvent

internal object StructureEvidenceEngine {
    fun direction(context: TrendContext): EvidenceDirection {
        if (context.failedBreakout != null || context.sweep != null ||
            context.event == TrendEvent.CONFLICT ||
            context.event == TrendEvent.REVERSAL_CANDIDATE_UP ||
            context.event == TrendEvent.REVERSAL_CANDIDATE_DOWN) return EvidenceDirection.NEUTRAL
        return when (context.structure.direction) {
            TrendDirection.UP -> EvidenceDirection.LONG
            TrendDirection.DOWN -> EvidenceDirection.SHORT
            TrendDirection.UNKNOWN -> EvidenceDirection.MISSING
            else -> EvidenceDirection.NEUTRAL
        }
    }
}
