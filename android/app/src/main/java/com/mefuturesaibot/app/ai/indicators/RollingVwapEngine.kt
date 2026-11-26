package com.mefuturesaibot.app.ai.indicators

import com.mefuturesaibot.app.ai.TimeframeHistory

/** Rolling typical-price VWAP approximation, not session-anchored trade VWAP. */
internal object RollingVwapEngine {
    fun calculate(history: TimeframeHistory, exchangeTimeMs: Long, period: Int = 20): Double? {
        val input = IndicatorInput.from(history, exchangeTimeMs) ?: return null
        if (period <= 0 || input.candleCount < period) return null
        var total = 0.0
        var weighted = 0.0
        for (candle in history.candles.takeLast(period)) {
            val volume = candle.volume.toDouble()
            val price = candle.high.toDouble() / 3 + candle.low.toDouble() / 3 + candle.close.toDouble() / 3
            if (!volume.isFinite() || volume < 0 || !price.isFinite() || price <= 0) return null
            total += volume
            weighted += volume * price
            if (!total.isFinite() || !weighted.isFinite()) return null
        }
        if (total <= 0) return null
        return (weighted / total).takeIf { it.isFinite() && it > 0 }
    }
}
