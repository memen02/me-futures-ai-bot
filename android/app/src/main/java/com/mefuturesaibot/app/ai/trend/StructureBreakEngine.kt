package com.mefuturesaibot.app.ai.trend

import com.mefuturesaibot.app.ai.TimeframeHistory
import com.mefuturesaibot.app.ai.indicators.IndicatorInput
import java.math.BigDecimal

internal enum class StructureBreakKind { BOS_UP, BOS_DOWN, CHOCH_UP, CHOCH_DOWN, UNCLASSIFIED_UP, UNCLASSIFIED_DOWN }
internal data class StructureBreak(val kind: StructureBreakKind, val level: BigDecimal, val closedAtMs: Long)

internal object StructureBreakEngine {
    fun detect(history: TimeframeHistory, exchangeTimeMs: Long, radius: Int = 2): StructureBreak? {
        val stable = history.copy(candles = history.candles.toList())
        val input = IndicatorInput.from(stable, exchangeTimeMs) ?: return null
        if (stable.candles.size < 2) return null
        val last = stable.candles.last()
        val previous = stable.candles[stable.candles.lastIndex-1]
        val swings = MarketStructureEngine.swings(stable, exchangeTimeMs, radius)
            ?.filter { it.confirmedAt <= last.openTime } ?: return null
        val direction = StructureTrendEngine.assess(swings, last.openTime).direction
        val high = swings.lastOrNull { it.kind == SwingKind.HIGH }?.price
        val low = swings.lastOrNull { it.kind == SwingKind.LOW }?.price
        if (high != null && low != null && high <= low) return null
        if (high != null && previous.close <= high && last.close > high) {
            val kind = when (direction) {
                TrendDirection.UP -> StructureBreakKind.BOS_UP
                TrendDirection.DOWN -> StructureBreakKind.CHOCH_UP
                else -> StructureBreakKind.UNCLASSIFIED_UP
            }
            return StructureBreak(kind, high, input.closedAtMs)
        }
        if (low != null && previous.close >= low && last.close < low) {
            val kind = when (direction) {
                TrendDirection.DOWN -> StructureBreakKind.BOS_DOWN
                TrendDirection.UP -> StructureBreakKind.CHOCH_DOWN
                else -> StructureBreakKind.UNCLASSIFIED_DOWN
            }
            return StructureBreak(kind, low, input.closedAtMs)
        }
        return null
    }
}
