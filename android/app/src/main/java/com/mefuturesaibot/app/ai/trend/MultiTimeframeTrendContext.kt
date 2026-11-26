package com.mefuturesaibot.app.ai.trend

import com.mefuturesaibot.app.ai.Timeframe
import com.mefuturesaibot.app.ai.TimeframeHistory

internal data class MultiTimeframeTrendContext(
    val symbol: String,
    val evaluatedAtMs: Long,
    val frames: Map<Timeframe, TrendContext>
) {
    val missing: Set<Timeframe> get() = Timeframe.entries.toSet() - frames.keys
}

internal object MultiTimeframeTrendEngine {
    fun calculate(symbol: String, histories: Map<Timeframe, TimeframeHistory>, exchangeTimeMs: Long): MultiTimeframeTrendContext {
        require(symbol.isNotBlank())
        require(exchangeTimeMs > 0)
        val frames = Timeframe.entries.mapNotNull { frame ->
            val history = histories[frame] ?: return@mapNotNull null
            if (history.symbol != symbol || history.timeframe != frame) return@mapNotNull null
            TrendContextEngine.calculate(history, exchangeTimeMs)?.let { frame to it }
        }.toMap()
        return MultiTimeframeTrendContext(symbol, exchangeTimeMs, java.util.Collections.unmodifiableMap(frames))
    }
}
