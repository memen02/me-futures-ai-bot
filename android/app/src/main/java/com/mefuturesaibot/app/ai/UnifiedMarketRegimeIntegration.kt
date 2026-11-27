package com.mefuturesaibot.app.ai

import com.mefuturesaibot.app.ai.trend.*
import java.util.Collections

internal enum class UnifiedRegime { UPTREND, DOWNTREND, SIDEWAYS, CONFLICT, UNKNOWN }
internal data class UnifiedRegimeFrame(val regime: UnifiedRegime, val volatility: VolatilityRegime,
    val closedAtMs: Long)

internal object UnifiedMarketRegimeIntegration {
    fun calculate(source: UnifiedTimeframeSnapshot, trend: UnifiedTrendSnapshot,
        earliest: Long, latest: Long): Map<Timeframe, UnifiedRegimeFrame> {
        val structures = UnifiedMarketStructureIntegration.calculate(source,trend,earliest,latest)
        val frames = structures.mapValues { (frame,structure) ->
            val context = trend.frames.getValue(frame)
            val direction = context.trend.direction
            val regime = when {
                structure.failedBreakout != null || structure.event == TrendEvent.CONFLICT -> UnifiedRegime.CONFLICT
                direction == TrendDirection.UNKNOWN || context.volatility == VolatilityRegime.UNKNOWN -> UnifiedRegime.UNKNOWN
                direction == TrendDirection.SIDEWAYS -> UnifiedRegime.SIDEWAYS
                direction == structure.structure.direction && direction == TrendDirection.UP -> UnifiedRegime.UPTREND
                direction == structure.structure.direction && direction == TrendDirection.DOWN -> UnifiedRegime.DOWNTREND
                structure.structure.direction == TrendDirection.UNKNOWN -> UnifiedRegime.UNKNOWN
                else -> UnifiedRegime.CONFLICT
            }
            UnifiedRegimeFrame(regime,context.volatility,structure.closedAtMs)
        }
        return Collections.unmodifiableMap(frames)
    }
}
