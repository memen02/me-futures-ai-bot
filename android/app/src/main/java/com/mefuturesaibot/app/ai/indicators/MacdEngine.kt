package com.mefuturesaibot.app.ai.indicators

import com.mefuturesaibot.app.ai.Timeframe
import com.mefuturesaibot.app.ai.TimeframeHistory

/**
 * MACD using SMA-seeded exponential averages.
 *
 * MACD line starts after 26 closes.
 * Signal and histogram start after 34 closes.
 *
 * Missing values represent insufficient history.
 */
internal data class MacdPoint(
    val macd: Double?,
    val signal: Double?,
    val histogram: Double?
)

internal data class MacdSnapshot(
    val symbol: String,
    val timeframe: Timeframe,
    val closedAtMs: Long,
    val candleCount: Int,
    val close: Double,
    val macd: Double?,
    val signal: Double?,
    val histogram: Double?
) {
    val allAvailable: Boolean
        get() =
            macd != null &&
            signal != null &&
            histogram != null
}

internal object MacdEngine {

    const val FAST_PERIOD = 12
    const val SLOW_PERIOD = 26
    const val SIGNAL_PERIOD = 9

    fun calculate(
        history: TimeframeHistory,
        exchangeTimeMs: Long
    ): MacdSnapshot? {

        val input = IndicatorInput.from(
            history,
            exchangeTimeMs
        ) ?: return null

        val point = series(input.closes)
            ?.lastOrNull()
            ?: return null

        return MacdSnapshot(
            symbol = input.symbol,
            timeframe = input.timeframe,
            closedAtMs = input.closedAtMs,
            candleCount = input.candleCount,
            close = input.close,
            macd = point.macd,
            signal = point.signal,
            histogram = point.histogram
        )
    }

    /**
     * Output indices match input close indices.
     *
     * Warm-up values are null.
     * Invalid input or numerical failure
     * returns null for the entire series.
     */
    fun series(
        closes: List<Double>
    ): List<MacdPoint>? {

        if (
            closes.any {
                !it.isFinite() || it < java.lang.Double.MIN_NORMAL
            }
        ) {
            return null
        }

        val fast = emaSeries(
            closes,
            FAST_PERIOD
        ) ?: return null

        val slow = emaSeries(
            closes,
            SLOW_PERIOD
        ) ?: return null

        val result = MutableList(
            closes.size
        ) {
            MacdPoint(null, null, null)
        }

        if (closes.size < SLOW_PERIOD) {
            return result.toList()
        }

        val macdValues = ArrayList<Double>(
            closes.size - SLOW_PERIOD + 1
        )

        val firstMacdIndex = SLOW_PERIOD - 1

        for (
            i in firstMacdIndex until closes.size
        ) {

            val fastValue = fast[i]
                ?: return null

            val slowValue = slow[i]
                ?: return null

            val macd = fastValue - slowValue

            if (!macd.isFinite()) {
                return null
            }

            macdValues.add(macd)
        }

        val signals = emaSeries(
            macdValues,
            SIGNAL_PERIOD
        ) ?: return null

        for (i in macdValues.indices) {

            val macd = macdValues[i]

            val signal = signals[i]

            val histogram = signal?.let {
                macd - it
            }

            if (
                histogram != null &&
                !histogram.isFinite()
            ) {
                return null
            }

            result[firstMacdIndex + i] =
                MacdPoint(
                    macd = macd,
                    signal = signal,
                    histogram = histogram
                )
        }

        return result.toList()
    }

    /**
     * Linear-time EMA series.
     *
     * The first EMA is seeded with SMA.
     * Signed values are allowed because
     * the MACD signal input can be negative.
     */
    private fun emaSeries(
        values: List<Double>,
        period: Int
    ): List<Double?>? {

        if (period <= 0) return null

        if (values.any { !it.isFinite() }) {
            return null
        }

        val result = MutableList<Double?>(
            values.size
        ) { null }

        if (values.size < period) {
            return result.toList()
        }

        var average = IndicatorMath.sma(
            values.subList(0, period),
            period
        ) ?: return null

        result[period - 1] = average

        val alpha =
            2.0 / (period.toDouble() + 1.0)

        for (i in period until values.size) {

            average =
                average * (1.0 - alpha) +
                values[i] * alpha

            if (!average.isFinite()) {
                return null
            }

            result[i] = average
        }

        return result.toList()
    }
}
