package com.mefuturesaibot.app.ai.indicators

import com.mefuturesaibot.app.ai.Timeframe
import com.mefuturesaibot.app.ai.TimeframeHistory
import com.mefuturesaibot.app.ai.MultiTimeframeHistory

/**
 * Shared validated closing-price input.
 *
 * Uses chronological, contiguous and closed candles.
 * Rejects stale or malformed history.
 *
 * The supplied exchange time must come from
 * the trusted exchange clock.
 */
internal class IndicatorInput private constructor(
    val symbol: String,
    val timeframe: Timeframe,
    val closedAtMs: Long,
    val closes: List<Double>
) {

    val candleCount: Int
        get() = closes.size

    val close: Double
        get() = closes.last()

    companion object {

        fun from(
            history: TimeframeHistory,
            exchangeTimeMs: Long
        ): IndicatorInput? {

            if (history.symbol.isBlank()) return null

            if (exchangeTimeMs <= 0L) return null

            if (history.observedAt <= 0L) return null

            if (history.observedAt > exchangeTimeMs) {
                return null
            }

            val candles = history.candles.toList()

            if (
                candles.size !in
                1..MultiTimeframeHistory.REQUEST_LIMIT
            ) {
                return null
            }

            val timeframe = history.timeframe

            val closes = ArrayList<Double>(
                candles.size
            )

            var expectedOpen: Long? = null

            var lastEnd = 0L

            for (candle in candles) {

                if (!timeframe.validOpen(candle.openTime)) {
                    return null
                }

                if (
                    expectedOpen != null &&
                    candle.openTime != expectedOpen
                ) {
                    return null
                }

                val end = try {
                    timeframe.nextOpen(candle.openTime)
                } catch (_: ArithmeticException) {
                    return null
                }

                if (end > history.observedAt) {
                    return null
                }

                if (candle.low.signum() <= 0) {
                    return null
                }

                if (candle.high < candle.low) {
                    return null
                }

                if (
                    candle.open !in candle.low..candle.high ||
                    candle.close !in candle.low..candle.high
                ) {
                    return null
                }

                if (candle.volume.signum() < 0) {
                    return null
                }

                val close = candle.close.toDouble()

                // Reject subnormal prices to reduce numerical underflow.
                if (
                    !close.isFinite() ||
                    close < java.lang.Double.MIN_NORMAL
                ) {
                    return null
                }

                closes.add(close)

                expectedOpen = end
                lastEnd = end
            }

            val expires = try {
                timeframe.nextOpen(lastEnd)
            } catch (_: ArithmeticException) {
                return null
            }

            if (
                exchangeTimeMs < lastEnd ||
                exchangeTimeMs >= expires
            ) {
                return null
            }

            return IndicatorInput(
                symbol = history.symbol,
                timeframe = timeframe,
                closedAtMs = lastEnd - 1L,
                closes = java.util.Collections
                    .unmodifiableList(closes)
            )
        }
    }
}
