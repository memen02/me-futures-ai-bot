package com.mefuturesaibot.app.ai.indicators

import com.mefuturesaibot.app.ai.TimeframeHistory

internal object WeightedMovingAverageEngine {
    fun calculate(history: TimeframeHistory, exchangeTimeMs: Long): Double? =
        IndicatorInput.from(history, exchangeTimeMs)?.let { value(it.closes) }

    fun value(closes: List<Double>, period: Int = 20): Double? {
        if (period <= 0 || closes.size < period || closes.any { !it.isFinite() || it <= 0 }) return null
        val divisor = period.toDouble() * (period.toDouble() + 1.0) / 2.0
        var sum = 0.0
        for (i in 0 until period) {
            sum += closes[closes.size - period + i] * ((i + 1.0) / divisor)
        }
        return sum.takeIf { it.isFinite() }
    }
}
