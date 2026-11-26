package com.mefuturesaibot.app.ai.indicators

import com.mefuturesaibot.app.ai.TimeframeHistory

internal object StochasticRsiEngine {
    fun calculate(history: TimeframeHistory, exchangeTimeMs: Long): Double? =
        IndicatorInput.from(history, exchangeTimeMs)?.let { value(it.closes) }

    fun value(closes: List<Double>, rsiPeriod: Int = 14, window: Int = 14): Double? {
        if (window <= 0 || window > closes.size) return null
        val series = RsiEngine.series(closes, rsiPeriod) ?: return null
        val recent = series.takeLast(window).filterNotNull()
        if (recent.size != window) return null
        val low = recent.minOrNull() ?: return null
        val high = recent.maxOrNull() ?: return null
        if (high == low) return null
        return (100.0 * (recent.last() - low) / (high - low)).takeIf { it.isFinite() && it in 0.0..100.0 }
    }
}
