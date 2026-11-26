package com.mefuturesaibot.app.ai.indicators

import com.mefuturesaibot.app.ai.TimeframeHistory
import kotlin.math.abs

internal object AtrEngine {
    fun calculate(history: TimeframeHistory, exchangeTimeMs: Long, period: Int = 14): Double? {
        val input = IndicatorInput.from(history, exchangeTimeMs) ?: return null
        val candles = history.candles
        if (period <= 0 || input.candleCount <= period) return null
        val ranges = ArrayList<Double>(candles.size - 1)
        for (i in 1 until candles.size) {
            val high = candles[i].high.toDouble()
            val low = candles[i].low.toDouble()
            val previous = input.closes[i - 1]
            val range = maxOf(high - low, abs(high - previous), abs(low - previous))
            if (!range.isFinite() || range < 0) return null
            ranges.add(range)
        }
        return IndicatorMath.rma(ranges, period)
    }
}
