package com.mefuturesaibot.app.ai.trend

import com.mefuturesaibot.app.ai.TimeframeHistory
import com.mefuturesaibot.app.ai.indicators.IndicatorInput

internal object RelativeVolumeEngine {
    fun calculate(history: TimeframeHistory, exchangeTimeMs: Long, period: Int = 20): Double? {
        val stable = history.copy(candles = history.candles.toList())
        IndicatorInput.from(stable, exchangeTimeMs) ?: return null
        if (period <= 0 || period >= stable.candles.size) return null
        val volumes = stable.candles.takeLast(period+1).map { it.volume.toDouble() }
        if (volumes.any { !it.isFinite() || it < 0 }) return null
        val baseline = volumes.dropLast(1).sumOf { it / period }
        if (!baseline.isFinite() || baseline <= 0) return null
        return (volumes.last()/baseline).takeIf { it.isFinite() }
    }
}
