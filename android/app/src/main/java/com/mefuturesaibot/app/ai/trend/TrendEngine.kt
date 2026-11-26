package com.mefuturesaibot.app.ai.trend

import com.mefuturesaibot.app.ai.indicators.IndicatorSnapshot

internal enum class TrendDirection { UP, DOWN, SIDEWAYS, UNKNOWN }
internal enum class TrendStrength { WEAK, MODERATE, STRONG, UNKNOWN }
internal data class TrendAssessment(val direction: TrendDirection, val strength: TrendStrength)

/** Initial heuristic, not a calibrated probability or entry signal. */
internal object TrendEngine {
    fun assess(snapshot: IndicatorSnapshot): TrendAssessment {
        val fast = snapshot.ema20
        val slow = snapshot.ema50
        val dmi = snapshot.directionalStrength
        if (fast == null || slow == null || dmi == null ||
            !fast.isFinite() || !slow.isFinite() || slow <= 0 ||
            listOf(dmi.adx, dmi.plusDi, dmi.minusDi).any { !it.isFinite() || it !in 0.0..100.0000001 }) {
            return TrendAssessment(TrendDirection.UNKNOWN, TrendStrength.UNKNOWN)
        }
        val strength = when {
            dmi.adx < 20 -> TrendStrength.WEAK
            dmi.adx < 25 -> TrendStrength.MODERATE
            else -> TrendStrength.STRONG
        }
        val direction = when {
            dmi.adx < 20 -> TrendDirection.SIDEWAYS
            fast > slow && dmi.plusDi > dmi.minusDi -> TrendDirection.UP
            fast < slow && dmi.minusDi > dmi.plusDi -> TrendDirection.DOWN
            else -> TrendDirection.UNKNOWN
        }
        return TrendAssessment(direction, strength)
    }
}
