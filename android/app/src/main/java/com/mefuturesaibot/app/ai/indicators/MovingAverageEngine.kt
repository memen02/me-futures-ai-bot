package com.mefuturesaibot.app.ai.indicators

import com.mefuturesaibot.app.ai.Timeframe
import com.mefuturesaibot.app.ai.TimeframeHistory
import com.mefuturesaibot.app.ai.MultiTimeframeHistory

/**
 * Moving averages calculated exclusively from
 * validated and closed candle history.
 *
 * Missing values represent insufficient history
 * or unavailable numerical results.
 */
internal data class MovingAverageSnapshot(
    val symbol: String,
    val timeframe: Timeframe,
    val closedAtMs: Long,
    val candleCount: Int,
    val close: Double,
    val ema9: Double?,
    val ema20: Double?,
    val ema50: Double?,
    val ema100: Double?,
    val ema200: Double?,
    val sma20: Double?,
    val sma50: Double?,
    val sma200: Double?
) {

    val allAvailable: Boolean
        get() = listOf(
            ema9,
            ema20,
            ema50,
            ema100,
            ema200,
            sma20,
            sma50,
            sma200
        ).all { it != null }
}

internal object MovingAverageEngine {

    fun calculate(
        history: TimeframeHistory,
        exchangeTimeMs: Long
    ): MovingAverageSnapshot? {

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

            if (!close.isFinite() || close <= 0.0) {
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

        return MovingAverageSnapshot(
            symbol = history.symbol,
            timeframe = timeframe,
            closedAtMs = lastEnd - 1L,
            candleCount = closes.size,
            close = closes.last(),

            ema9 = IndicatorMath.ema(closes, 9),
            ema20 = IndicatorMath.ema(closes, 20),
            ema50 = IndicatorMath.ema(closes, 50),
            ema100 = IndicatorMath.ema(closes, 100),
            ema200 = IndicatorMath.ema(closes, 200),

            sma20 = IndicatorMath.sma(closes, 20),
            sma50 = IndicatorMath.sma(closes, 50),
            sma200 = IndicatorMath.sma(closes, 200)
        )
    }
}
