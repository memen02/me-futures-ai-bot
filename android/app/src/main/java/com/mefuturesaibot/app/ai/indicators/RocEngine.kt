package com.mefuturesaibot.app.ai.indicators

import com.mefuturesaibot.app.ai.TimeframeHistory

internal object RocEngine {
    fun calculate(history: TimeframeHistory, exchangeTimeMs: Long): Double? =
        IndicatorInput.from(history, exchangeTimeMs)?.let { value(it.closes) }

    fun value(closes: List<Double>, period: Int = 12): Double? {
        if (period <= 0 || closes.size <= period || closes.any { !it.isFinite() || it <= 0 }) return null
        val previous = closes[closes.lastIndex - period]
        return ((closes.last() / previous - 1.0) * 100.0).takeIf { it.isFinite() }
    }
}
