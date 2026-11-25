package com.mefuturesaibot.app.ai.indicators

import kotlin.math.sqrt

/**
 * Pure mathematical foundation for technical indicators.
 *
 * Null represents unavailable or invalid results.
 * Callers must supply chronological, validated,
 * closed-candle data.
 */
internal object IndicatorMath {

    fun sma(
        values: List<Double>,
        period: Int
    ): Double? {
        if (!valid(values, period)) return null

        return mean(
            values,
            values.size - period,
            period
        )
    }

    fun ema(
        values: List<Double>,
        period: Int
    ): Double? {
        if (!valid(values, period)) return null

        val alpha = 2.0 / (period.toDouble() + 1.0)

        return smooth(values, period, alpha)
    }

    fun rma(
        values: List<Double>,
        period: Int
    ): Double? {
        if (!valid(values, period)) return null

        val alpha = 1.0 / period.toDouble()

        return smooth(values, period, alpha)
    }

    fun standardDeviation(
        values: List<Double>,
        period: Int
    ): Double? {
        if (!valid(values, period)) return null

        val start = values.size - period

        val average =
            mean(values, start, period)
                ?: return null

        var variance = 0.0

        for (i in start until values.size) {
            val difference = values[i] - average

            variance +=
                difference * difference /
                period.toDouble()

            if (!variance.isFinite()) return null
        }

        return sqrt(variance)
            .takeIf { it.isFinite() }
    }

    private fun smooth(
        values: List<Double>,
        period: Int,
        alpha: Double
    ): Double? {
        var result =
            mean(values, 0, period)
                ?: return null

        for (i in period until values.size) {
            result =
                result * (1.0 - alpha) +
                values[i] * alpha

            if (!result.isFinite()) return null
        }

        return result
    }

    private fun mean(
        values: List<Double>,
        start: Int,
        period: Int
    ): Double? {
        var result = 0.0

        for (i in start until start + period) {
            result +=
                values[i] / period.toDouble()

            if (!result.isFinite()) return null
        }

        return result
    }

    private fun valid(
        values: List<Double>,
        period: Int
    ): Boolean {
        return period > 0 &&
            values.size >= period &&
            values.all { it.isFinite() }
    }
}
