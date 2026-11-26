package com.mefuturesaibot.app.ai.trend

internal enum class TrendEvent { CONTINUATION_UP, CONTINUATION_DOWN, REVERSAL_CANDIDATE_UP, REVERSAL_CANDIDATE_DOWN, PULLBACK_CANDIDATE, CONFLICT, WAIT }

internal object TrendEventEngine {
    fun assess(trend: TrendAssessment, structure: StructureTrend, breakout: StructureBreak?, sweep: LiquiditySweep?): TrendEvent {
        if (sweep != null) return TrendEvent.WAIT
        if (breakout != null) {
            return when (breakout.kind) {
                StructureBreakKind.BOS_UP -> if (trend.direction == TrendDirection.UP) TrendEvent.CONTINUATION_UP else TrendEvent.WAIT
                StructureBreakKind.BOS_DOWN -> if (trend.direction == TrendDirection.DOWN) TrendEvent.CONTINUATION_DOWN else TrendEvent.WAIT
                StructureBreakKind.CHOCH_UP -> TrendEvent.REVERSAL_CANDIDATE_UP
                StructureBreakKind.CHOCH_DOWN -> TrendEvent.REVERSAL_CANDIDATE_DOWN
                else -> TrendEvent.WAIT
            }
        }
        val directional = setOf(TrendDirection.UP, TrendDirection.DOWN)
        if (trend.direction in directional && structure.direction in directional && trend.direction != structure.direction)
            return TrendEvent.CONFLICT
        return TrendEvent.WAIT
    }
}
