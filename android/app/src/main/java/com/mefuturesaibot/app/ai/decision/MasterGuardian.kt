package com.mefuturesaibot.app.ai.decision

import com.mefuturesaibot.app.ai.trend.TrendContext
import com.mefuturesaibot.app.ai.trend.TrendEvent
import com.mefuturesaibot.app.ai.trend.VolatilityRegime

internal object MasterGuardian {
    fun blocks(context: TrendContext): Boolean =
        context.failedBreakout != null || context.sweep != null ||
        context.volatility == VolatilityRegime.UNKNOWN ||
        context.volatility == VolatilityRegime.EXPANDED ||
        context.event in setOf(TrendEvent.CONFLICT, TrendEvent.REVERSAL_CANDIDATE_UP, TrendEvent.REVERSAL_CANDIDATE_DOWN)
}
