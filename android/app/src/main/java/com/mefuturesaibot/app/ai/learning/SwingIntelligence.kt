package com.mefuturesaibot.app.ai.learning

import com.mefuturesaibot.app.ai.Timeframe
import com.mefuturesaibot.app.ai.indicators.IndicatorSnapshot
import com.mefuturesaibot.app.ai.trend.TrendEngine
import com.mefuturesaibot.app.ai.trend.TrendDirection

internal object SwingIntelligence {
    // Directional context only, not an order or price forecast.
    fun assess(symbol: String, frames: Map<Timeframe, IndicatorSnapshot>, nowMs: Long): TrendDirection {
        if (symbol.isBlank() || nowMs <= 0) return TrendDirection.UNKNOWN
        val required = listOf(Timeframe.D1,Timeframe.D3,Timeframe.W1)
        val directions = required.map { frame ->
            val s = frames[frame] ?: return TrendDirection.UNKNOWN
            if (s.symbol != symbol || s.timeframe != frame || s.closedAtMs <= 0 ||
                s.closedAtMs >= nowMs || !s.close.isFinite() || s.close <= 0) return TrendDirection.UNKNOWN
            val end = s.closedAtMs+1
            if (!frame.validOpen(end) || nowMs >= frame.nextOpen(end)) return TrendDirection.UNKNOWN
            TrendEngine.assess(s).direction
        }
        return directions.first().takeIf { direction ->
            direction != TrendDirection.UNKNOWN && directions.all { it == direction }
        } ?: TrendDirection.UNKNOWN
    }
}
