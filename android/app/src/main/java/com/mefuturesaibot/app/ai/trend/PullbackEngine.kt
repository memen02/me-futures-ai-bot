package com.mefuturesaibot.app.ai.trend

import com.mefuturesaibot.app.ai.indicators.IndicatorSnapshot

internal object PullbackEngine {
    fun candidate(snapshot: IndicatorSnapshot, structure: StructureTrend): Boolean {
        val trend = TrendEngine.assess(snapshot)
        val fast = snapshot.ema20 ?: return false
        val slow = snapshot.ema50 ?: return false
        val roc = snapshot.roc12 ?: return false
        val price = snapshot.close
        if (listOf(fast,slow,roc,price).any { !it.isFinite() } || price <= 0) return false
        return when (structure.direction) {
            TrendDirection.UP -> trend.direction == TrendDirection.UP && price in slow..fast && roc < 0
            TrendDirection.DOWN -> trend.direction == TrendDirection.DOWN && price in fast..slow && roc > 0
            else -> false
        }
    }
}
