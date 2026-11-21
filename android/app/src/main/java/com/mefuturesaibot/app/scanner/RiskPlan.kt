package com.mefuturesaibot.app.scanner

import com.mefuturesaibot.app.exchange.Candle
import kotlin.math.abs
import kotlin.math.min

internal data class Quote(val bid: Double, val ask: Double, val at: Long) {
    fun problem(now: Long): String? = when {
        now - at !in -1000L..5000L -> "kuotasi kedaluwarsa"
        !bid.isFinite() || !ask.isFinite() || bid <= 0 || ask < bid -> "bid/ask tidak valid"
        (ask - bid) / bid > 0.0005 -> "spread di atas 0,05%"
        else -> null
    }
}

internal data class Levels(val support: Double, val resistance: Double) {
    companion object {
        fun fromClosed(bars: List<Candle>): Levels {
            val window = bars.takeLast(20)
            require(window.size == 20)
            return Levels(window.minOf { it.low.toDouble() }, window.maxOf { it.high.toDouble() })
        }
    }
}

internal data class RiskPlan(val side: Int, val entry: Double, val stop: Double, val target: Double, val quantity: Double) {
    companion object {
        const val FEE = 0.0005
        const val SLIP = 0.0002
        const val RISK = 0.005
        const val DAY_LIMIT = 0.015

        fun build(side: Int, q: Quote, levels: Levels, equity: Double, now: Long): RiskPlan {
            require(side == 1 || side == -1)
            q.problem(now)?.let { throw IllegalArgumentException(it) }
            require(equity.isFinite() && equity > 0) { "ekuitas tidak tersedia" }
            val entry = (if (side == 1) q.ask else q.bid) * (1 + side * SLIP)
            val stop = if (side == 1) levels.support else levels.resistance
            val target = if (side == 1) levels.resistance else levels.support
            require(stop > 0 && target.isFinite() && side * (entry - stop) > 0 && side * (target - entry) > 0) {
                "harga di luar support/resistance"
            }
            val exitStop = stop * (1 - side * SLIP)
            val exitTarget = target * (1 - side * SLIP)
            val loss = abs(entry - exitStop) + FEE * (entry + exitStop)
            val profit = side * (exitTarget - entry) - FEE * (entry + exitTarget)
            require(profit >= 2 * loss) { "rasio laba/rugi bersih di bawah 2" }
            val qty = min(equity * RISK / loss, equity / (entry * (1 + FEE)))
            return RiskPlan(side, entry, stop, target, qty)
        }
    }
}
