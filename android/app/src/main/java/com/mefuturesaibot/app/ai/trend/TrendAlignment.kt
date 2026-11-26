package com.mefuturesaibot.app.ai.trend

import com.mefuturesaibot.app.ai.Timeframe
import com.mefuturesaibot.app.ai.indicators.MultiTimeframeIndicators

internal enum class Alignment { ALIGNED_UP, ALIGNED_DOWN, CONFLICT, UNRESOLVED }

internal object TrendAlignment {
    fun assess(snapshot: MultiTimeframeIndicators, required: Set<Timeframe>): Alignment {
        if (required.isEmpty()) return Alignment.UNRESOLVED
        val directions = required.map { frame ->
            val input = snapshot.frames[frame] ?: return Alignment.UNRESOLVED
            if (input.symbol != snapshot.symbol || input.timeframe != frame) return Alignment.UNRESOLVED
            TrendEngine.assess(input).direction
        }
        return when {
            TrendDirection.UP in directions && TrendDirection.DOWN in directions -> Alignment.CONFLICT
            directions.all { it == TrendDirection.UP } -> Alignment.ALIGNED_UP
            directions.all { it == TrendDirection.DOWN } -> Alignment.ALIGNED_DOWN
            else -> Alignment.UNRESOLVED
        }
    }
}
