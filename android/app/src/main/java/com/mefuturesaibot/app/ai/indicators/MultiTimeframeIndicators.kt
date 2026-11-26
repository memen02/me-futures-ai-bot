package com.mefuturesaibot.app.ai.indicators

import com.mefuturesaibot.app.ai.Timeframe
import com.mefuturesaibot.app.ai.TimeframeHistory

internal data class MultiTimeframeIndicators(
    val symbol: String,
    val evaluatedAtMs: Long,
    val frames: Map<Timeframe, IndicatorSnapshot>
) {
    val missing: Set<Timeframe> get() = Timeframe.entries.toSet() - frames.keys
}

internal object MultiTimeframeIndicatorEngine {
    fun calculate(symbol: String, histories: Map<Timeframe, TimeframeHistory>, exchangeTimeMs: Long): MultiTimeframeIndicators {
        require(symbol.isNotBlank())
        val frames = Timeframe.entries.mapNotNull { frame ->
            val history = histories[frame] ?: return@mapNotNull null
            if (history.symbol != symbol || history.timeframe != frame) return@mapNotNull null
            IndicatorSnapshotEngine.calculate(history, exchangeTimeMs)?.let { frame to it }
        }.toMap()
        return MultiTimeframeIndicators(symbol, exchangeTimeMs, java.util.Collections.unmodifiableMap(frames))
    }
}
