package com.mefuturesaibot.app.ai.trend

internal enum class HighStructure { HH, LH, EQUAL, UNKNOWN }
internal enum class LowStructure { HL, LL, EQUAL, UNKNOWN }
internal data class StructureTrend(val high: HighStructure, val low: LowStructure, val direction: TrendDirection)

internal object StructureTrendEngine {
    fun assess(swings: List<SwingPoint>, asOfMs: Long): StructureTrend {
        val confirmed = swings.filter { it.confirmedAt <= asOfMs && it.openTime < it.confirmedAt }
            .sortedBy { it.openTime }
        val highs = confirmed.filter { it.kind == SwingKind.HIGH }.distinctBy { it.openTime }.takeLast(2)
        val lows = confirmed.filter { it.kind == SwingKind.LOW }.distinctBy { it.openTime }.takeLast(2)
        val high = if (highs.size < 2) HighStructure.UNKNOWN else when {
            highs[1].price > highs[0].price -> HighStructure.HH
            highs[1].price < highs[0].price -> HighStructure.LH
            else -> HighStructure.EQUAL
        }
        val low = if (lows.size < 2) LowStructure.UNKNOWN else when {
            lows[1].price > lows[0].price -> LowStructure.HL
            lows[1].price < lows[0].price -> LowStructure.LL
            else -> LowStructure.EQUAL
        }
        val direction = when {
            high == HighStructure.HH && low == LowStructure.HL -> TrendDirection.UP
            high == HighStructure.LH && low == LowStructure.LL -> TrendDirection.DOWN
            else -> TrendDirection.UNKNOWN
        }
        return StructureTrend(high, low, direction)
    }
}
