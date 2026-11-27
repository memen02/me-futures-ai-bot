package com.mefuturesaibot.app.ai

import com.mefuturesaibot.app.ai.trend.TrendContext
import com.mefuturesaibot.app.ai.trend.TrendContextEngine
import java.util.Collections

internal data class UnifiedTrendSnapshot(val symbol: String, val evaluatedAtMs: Long,
    val frames: Map<Timeframe, TrendContext>) {
    val missing: Set<Timeframe> get() = Timeframe.entries.toSet() - frames.keys
}

// Advisory context only. Missing frames are not directional votes.
internal object UnifiedTrendIntegration {
    fun calculate(snapshot: UnifiedTimeframeSnapshot): UnifiedTrendSnapshot? {
        if (!snapshot.fresh(snapshot.earliest,snapshot.latest)) return null
        val frames = snapshot.frames.mapNotNull { (frame,history) ->
            TrendContextEngine.calculate(history,snapshot.latest)?.let { frame to it }
        }.toMap()
        return UnifiedTrendSnapshot(snapshot.symbol,snapshot.latest,Collections.unmodifiableMap(frames))
    }
}
