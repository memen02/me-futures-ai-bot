package com.mefuturesaibot.app.ai.trend

import com.mefuturesaibot.app.ai.TimeframeHistory
import com.mefuturesaibot.app.ai.indicators.IndicatorInput
import java.math.BigDecimal

internal data class LiquiditySweep(val upperLevel: BigDecimal?, val lowerLevel: BigDecimal?, val closedAtMs: Long)

internal object LiquiditySweepEngine {
    fun detect(history: TimeframeHistory, exchangeTimeMs: Long, radius: Int = 2): LiquiditySweep? {
        val stable = history.copy(candles = history.candles.toList())
        val input = IndicatorInput.from(stable, exchangeTimeMs) ?: return null
        val last = stable.candles.last()
        val swings = MarketStructureEngine.swings(stable, exchangeTimeMs, radius)
            ?.filter { it.confirmedAt <= last.openTime } ?: return null
        val high = swings.lastOrNull { it.kind == SwingKind.HIGH }?.price
        val low = swings.lastOrNull { it.kind == SwingKind.LOW }?.price
        if (high != null && low != null && high <= low) return null
        val upper = high?.takeIf { last.high > it && last.open <= it && last.close <= it }
        val lower = low?.takeIf { last.low < it && last.open >= it && last.close >= it }
        if (upper == null && lower == null) return null
        return LiquiditySweep(upper, lower, input.closedAtMs)
    }
}
