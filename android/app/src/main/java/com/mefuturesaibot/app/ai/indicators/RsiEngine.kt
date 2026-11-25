package com.mefuturesaibot.app.ai.indicators

import com.mefuturesaibot.app.ai.Timeframe
import com.mefuturesaibot.app.ai.TimeframeHistory

/**
 * Wilder Relative Strength Index.
 *
 * The first value requires period + 1 closes.
 * Flat prices use the neutral value of 50.
 *
 * RSI describes momentum, not trade direction.
 */
internal data class RsiSnapshot(
    val symbol: String,
    val timeframe: Timeframe,
    val closedAtMs: Long,
    val candleCount: Int,
    val close: Double,
    val rsi14: Double?
)

internal object RsiEngine {

    const val PERIOD = 14

    fun calculate(
        history: TimeframeHistory,
        exchangeTimeMs: Long
    ): RsiSnapshot? {

        val input = IndicatorInput.from(
            history,
            exchangeTimeMs
        ) ?: return null

        return RsiSnapshot(
            symbol = input.symbol,
            timeframe = input.timeframe,
            closedAtMs = input.closedAtMs,
            candleCount = input.candleCount,
            close = input.close,
            rsi14 = series(input.closes)
                ?.lastOrNull()
        )
    }

    /**
     * Results align with the input close indices.
     * Warm-up positions contain null.
     *
     * Invalid input or numerical failure
     * returns null for the entire series.
     */
    fun series(
        closes: List<Double>,
        period: Int = PERIOD
    ): List<Double?>? {

        if (period <= 0) return null

        if (
            closes.any {
                !it.isFinite() || it < java.lang.Double.MIN_NORMAL
            }
        ) {
            return null
        }

        val result = MutableList<Double?>(
            closes.size
        ) { null }

        if (closes.size <= period) {
            return result.toList()
        }

        var averageGain = 0.0
        var averageLoss = 0.0

        val divisor = period.toDouble()

        for (i in 1..period) {

            val change =
                closes[i] - closes[i - 1]

            if (!change.isFinite()) {
                return null
            }

            val gain = change.coerceAtLeast(0.0)

            val loss = (-change)
                .coerceAtLeast(0.0)

            averageGain += gain / divisor
            averageLoss += loss / divisor
        }

        result[period] = value(
            averageGain,
            averageLoss
        ) ?: return null

        val alpha = 1.0 / divisor

        for (i in period + 1 until closes.size) {

            val change =
                closes[i] - closes[i - 1]

            if (!change.isFinite()) {
                return null
            }

            val gain = change.coerceAtLeast(0.0)

            val loss = (-change)
                .coerceAtLeast(0.0)

            averageGain =
                averageGain * (1.0 - alpha) +
                gain * alpha

            averageLoss =
                averageLoss * (1.0 - alpha) +
                loss * alpha

            result[i] = value(
                averageGain,
                averageLoss
            ) ?: return null
        }

        return result.toList()
    }

    private fun value(
        gain: Double,
        loss: Double
    ): Double? {

        if (
            !gain.isFinite() ||
            !loss.isFinite() ||
            gain < 0.0 ||
            loss < 0.0
        ) {
            return null
        }

        if (gain == 0.0 && loss == 0.0) {
            return 50.0
        }

        if (loss == 0.0) return 100.0

        if (gain == 0.0) return 0.0

        // Scale both components to avoid
        // overflow in gain + loss or gain / loss.
        val scale = maxOf(gain, loss)

        val scaledGain = gain / scale
        val scaledLoss = loss / scale

        val rsi = 100.0 * (
            scaledGain /
                (scaledGain + scaledLoss)
        )

        return rsi.takeIf {
            it.isFinite() && it in 0.0..100.0
        }
    }
}
