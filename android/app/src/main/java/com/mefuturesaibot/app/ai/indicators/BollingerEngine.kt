package com.mefuturesaibot.app.ai.indicators

import com.mefuturesaibot.app.ai.TimeframeHistory

internal data class BollingerBands(val middle: Double, val upper: Double, val lower: Double)

internal object BollingerEngine {
    fun calculate(history: TimeframeHistory, exchangeTimeMs: Long): BollingerBands? =
        IndicatorInput.from(history, exchangeTimeMs)?.let { bands(it.closes) }

    fun bands(closes: List<Double>, period: Int = 20, multiplier: Double = 2.0): BollingerBands? {
        if (!multiplier.isFinite() || multiplier <= 0 || closes.any { !it.isFinite() || it <= 0 }) return null
        val middle = IndicatorMath.sma(closes, period) ?: return null
        val deviation = IndicatorMath.standardDeviation(closes, period) ?: return null
        val upper = middle + multiplier * deviation
        val lower = middle - multiplier * deviation
        if (!upper.isFinite() || !lower.isFinite()) return null
        return BollingerBands(middle, upper, lower)
    }
}
