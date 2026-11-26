package com.mefuturesaibot.app.ai.trend

import com.mefuturesaibot.app.ai.TimeframeHistory
import com.mefuturesaibot.app.ai.indicators.IndicatorInput

internal enum class FailedBreakout { UP, DOWN }

internal object FailedBreakoutEngine {
    // Detect a previously closed breakout that returns across its level on the next candle.
    fun detect(history: TimeframeHistory, exchangeTimeMs: Long, radius: Int = 2): FailedBreakout? {
        val stable = history.copy(candles = history.candles.toList())
        IndicatorInput.from(stable, exchangeTimeMs) ?: return null
        if (stable.candles.size < 3) return null
        val last = stable.candles.last()
        val prior = stable.copy(candles = stable.candles.dropLast(1), observedAt = last.openTime)
        val breakout = StructureBreakEngine.detect(prior, last.openTime, radius) ?: return null
        return when (breakout.kind) {
            StructureBreakKind.BOS_UP, StructureBreakKind.CHOCH_UP, StructureBreakKind.UNCLASSIFIED_UP ->
                if (last.close <= breakout.level) FailedBreakout.UP else null
            StructureBreakKind.BOS_DOWN, StructureBreakKind.CHOCH_DOWN, StructureBreakKind.UNCLASSIFIED_DOWN ->
                if (last.close >= breakout.level) FailedBreakout.DOWN else null
        }
    }
}
