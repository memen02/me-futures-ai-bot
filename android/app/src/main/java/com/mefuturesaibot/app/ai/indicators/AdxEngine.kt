package com.mefuturesaibot.app.ai.indicators

import com.mefuturesaibot.app.ai.TimeframeHistory
import kotlin.math.abs

internal data class DirectionalStrength(val adx: Double, val plusDi: Double, val minusDi: Double)

internal object AdxEngine {
    fun calculate(history: TimeframeHistory, exchangeTimeMs: Long, period: Int = 14): DirectionalStrength? {
        val input = IndicatorInput.from(history, exchangeTimeMs) ?: return null
        val candles = history.candles
        return value(candles.map { it.high.toDouble() }, candles.map { it.low.toDouble() }, input.closes, period)
    }

    fun value(high: List<Double>, low: List<Double>, close: List<Double>, period: Int = 14): DirectionalStrength? {
        val n = close.size
        if (period <= 0 || period > n / 2 || high.size != n || low.size != n) return null
        if (high.indices.any { !high[it].isFinite() || !low[it].isFinite() || !close[it].isFinite() ||
                low[it] <= 0 || close[it] !in low[it]..high[it] }) return null
        var tr = 0.0
        var plus = 0.0
        var minus = 0.0
        var p = 0.0
        var m = 0.0
        val dx = ArrayList<Double>()
        val alpha = 1.0 / period
        for (i in 1 until n) {
            val range = maxOf(high[i] - low[i], abs(high[i] - close[i-1]), abs(low[i] - close[i-1]))
            val up = high[i] - high[i-1]
            val down = low[i-1] - low[i]
            val pos = if (up > down && up > 0) up else 0.0
            val neg = if (down > up && down > 0) down else 0.0
            if (i <= period) {
                tr += range * alpha
                plus += pos * alpha
                minus += neg * alpha
            } else {
                tr = tr * (1-alpha) + range * alpha
                plus = plus * (1-alpha) + pos * alpha
                minus = minus * (1-alpha) + neg * alpha
            }
            if (!tr.isFinite() || !plus.isFinite() || !minus.isFinite()) return null
            if (i >= period) {
                p = if (tr > 0) 100 * (plus / tr) else 0.0
                m = if (tr > 0) 100 * (minus / tr) else 0.0
                val sum = p + m
                dx.add(if (sum > 0) 100 * abs(p-m) / sum else 0.0)
            }
        }
        val adx = IndicatorMath.rma(dx, period) ?: return null
        return DirectionalStrength(adx, p, m).takeIf { listOf(it.adx,it.plusDi,it.minusDi).all { v -> v.isFinite() && v in 0.0..100.0000001 } }
    }
}
