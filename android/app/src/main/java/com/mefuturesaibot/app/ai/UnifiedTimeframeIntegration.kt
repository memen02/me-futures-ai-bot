package com.mefuturesaibot.app.ai

import java.util.Collections

// Capture only. Consumers must revalidate against current trusted clock bounds before publication.
internal class UnifiedTimeframeSnapshot private constructor(
    val symbol: String, val earliest: Long, val latest: Long,
    val frames: Map<Timeframe, TimeframeHistory>
) {
    val missing: Set<Timeframe> get() = Timeframe.entries.toSet() - frames.keys

    companion object {
        fun capture(symbol: String, histories: Map<Timeframe, TimeframeHistory>,
            earliest: Long, latest: Long): UnifiedTimeframeSnapshot? {
            if (!UnifiedMarketDataProvider.validSymbol(symbol) || earliest <= 0 ||
                latest < earliest || histories.size > Timeframe.entries.size) return null
            val frames = Timeframe.entries.mapNotNull { frame ->
                val history = histories[frame] ?: return@mapNotNull null
                RealtimeCandleIntegration.validate(symbol,frame,history,earliest,latest,50)
                    ?.let { frame to it }
            }.toMap()
            return UnifiedTimeframeSnapshot(symbol,earliest,latest,Collections.unmodifiableMap(frames))
        }
    }

    fun fresh(earliest: Long, latest: Long): Boolean =
        frames.isNotEmpty() && earliest >= this.earliest && latest >= this.latest &&
            latest >= earliest && frames.values.all { it.fresh(earliest) && it.fresh(latest) }
}
