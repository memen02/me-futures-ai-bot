package com.mefuturesaibot.app.ai

import com.mefuturesaibot.app.ai.trend.*
import java.util.Collections

internal data class UnifiedStructureFrame(val closedAtMs: Long, val structure: StructureTrend,
    val breakout: StructureBreak?, val failedBreakout: FailedBreakout?, val event: TrendEvent)

internal object UnifiedMarketStructureIntegration {
    fun calculate(source: UnifiedTimeframeSnapshot, trend: UnifiedTrendSnapshot,
        earliest: Long, latest: Long): Map<Timeframe, UnifiedStructureFrame> {
        if (!source.fresh(earliest,latest) || source.symbol != trend.symbol ||
            source.latest != trend.evaluatedAtMs) return emptyMap()
        val frames = trend.frames.mapNotNull { (frame,context) ->
            val history = source.frames[frame] ?: return@mapNotNull null
            val closed = frame.nextOpen(requireNotNull(history.lastOpen))-1L
            val indicators = context.indicators
            if (indicators.symbol != source.symbol || indicators.timeframe != frame ||
                indicators.closedAtMs != closed ||
                (context.breakout != null && context.breakout.closedAtMs != closed)) return@mapNotNull null
            frame to UnifiedStructureFrame(closed,context.structure,context.breakout,
                context.failedBreakout,context.event)
        }.toMap()
        return Collections.unmodifiableMap(frames)
    }
}
