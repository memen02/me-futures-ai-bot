package com.mefuturesaibot.app.ai.trend

import com.mefuturesaibot.app.ai.TimeframeHistory
import com.mefuturesaibot.app.ai.indicators.IndicatorInput
import java.math.BigDecimal

internal enum class SwingKind { HIGH, LOW }
internal data class SwingPoint(val kind: SwingKind, val openTime: Long, val price: BigDecimal, val confirmedAt: Long)

internal object MarketStructureEngine {
    fun swings(history: TimeframeHistory, exchangeTimeMs: Long, radius: Int = 2): List<SwingPoint>? {
        val stable = history.copy(candles = history.candles.toList())
        IndicatorInput.from(stable, exchangeTimeMs) ?: return null
        if (radius !in 1..20) return null
        val c = stable.candles
        val result = mutableListOf<SwingPoint>()
        for (i in radius until c.size-radius) {
            val pivot = c[i]
            val neighbors = (i-radius..i+radius).filter { it != i }
            val confirmedAt = stable.timeframe.nextOpen(c[i+radius].openTime)
            if (neighbors.all { pivot.high > c[it].high })
                result.add(SwingPoint(SwingKind.HIGH, pivot.openTime, pivot.high, confirmedAt))
            if (neighbors.all { pivot.low < c[it].low })
                result.add(SwingPoint(SwingKind.LOW, pivot.openTime, pivot.low, confirmedAt))
        }
        return result.toList()
    }
}
