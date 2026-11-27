package com.mefuturesaibot.app.ai.backtest

internal object HistoricalDataIntegrity {
    fun valid(bars: List<HistoricalBar>, intervalMs: Long): Boolean {
        if (intervalMs <= 0 || bars.size !in 2..100000) return false
        var previous: Long? = null
        for (bar in bars) {
            if (bar.openTime < 0 || bar.openTime > Long.MAX_VALUE - intervalMs) return false
            if (previous != null && bar.openTime - previous != intervalMs) return false
            if (listOf(bar.open,bar.high,bar.low,bar.close).any { !it.isFinite() || it <= 0 }) return false
            if (!bar.volume.isFinite() || bar.volume < 0) return false
            if (bar.high < maxOf(bar.open,bar.close,bar.low) || bar.low > minOf(bar.open,bar.close)) return false
            previous = bar.openTime
        }
        return true
    }
}
