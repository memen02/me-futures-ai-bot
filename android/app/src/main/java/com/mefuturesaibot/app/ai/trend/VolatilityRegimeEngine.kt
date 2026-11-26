package com.mefuturesaibot.app.ai.trend

import com.mefuturesaibot.app.ai.indicators.IndicatorSnapshot

internal enum class VolatilityRegime { COMPRESSED, NORMAL, EXPANDED, UNKNOWN }

internal object VolatilityRegimeEngine {
    // Compare normalized ATR with a same-symbol, same-timeframe historical baseline.
    fun assess(snapshot: IndicatorSnapshot, baselineAtrFraction: Double): VolatilityRegime {
        val atr = snapshot.atr14 ?: return VolatilityRegime.UNKNOWN
        val close = snapshot.close
        if (!atr.isFinite() || atr < 0 || !close.isFinite() || close <= 0 ||
            !baselineAtrFraction.isFinite() || baselineAtrFraction <= 0) return VolatilityRegime.UNKNOWN
        val ratio = (atr / close) / baselineAtrFraction
        if (!ratio.isFinite()) return VolatilityRegime.UNKNOWN
        return when {
            ratio < 0.7 -> VolatilityRegime.COMPRESSED
            ratio > 1.5 -> VolatilityRegime.EXPANDED
            else -> VolatilityRegime.NORMAL
        }
    }
}
