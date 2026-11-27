package com.mefuturesaibot.app.ai

import java.util.Collections

// Only trusted exchange-clock bounds may be supplied by the integration owner.
// Reject malformed, stale or partially closed history; never synthesize candles.
internal object RealtimeCandleIntegration {
    fun validate(symbol: String, frame: Timeframe, history: TimeframeHistory,
        earliest: Long, latest: Long, minimum: Int): TimeframeHistory? {
        if (!UnifiedMarketDataProvider.validSymbol(symbol) || history.symbol != symbol ||
            history.timeframe != frame || earliest <= 0 || latest < earliest ||
            history.observedAt <= 0 || history.observedAt > earliest ||
            minimum !in 1..MultiTimeframeHistory.REQUEST_LIMIT ||
            history.candles.size !in minimum..MultiTimeframeHistory.REQUEST_LIMIT) return null
        return try {
            val candles = ArrayList(history.candles)
            if (candles.size !in minimum..MultiTimeframeHistory.REQUEST_LIMIT) return null
            var previous: Long? = null
            for (c in candles) {
                if (!frame.validOpen(c.openTime) || frame.nextOpen(c.openTime) > history.observedAt ||
                    c.low.signum() <= 0 || c.volume.signum() < 0 ||
                    c.open < c.low || c.open > c.high || c.close < c.low || c.close > c.high ||
                    listOf(c.open,c.high,c.low,c.close,c.volume).any {
                        it.precision() > 64 || it.scale() !in -32..32
                    }) return null
                val prior = previous
                if (prior != null && frame.nextOpen(prior) != c.openTime) return null
                previous = c.openTime
            }
            val copy = history.copy(candles = Collections.unmodifiableList(candles))
            copy.takeIf { it.fresh(earliest) && it.fresh(latest) }
        } catch (_: RuntimeException) { null }
    }
}
