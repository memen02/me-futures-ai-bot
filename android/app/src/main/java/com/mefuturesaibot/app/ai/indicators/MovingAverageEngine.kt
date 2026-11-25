package com.mefuturesaibot.app.ai.indicators

import com.mefuturesaibot.app.ai.Timeframe
import com.mefuturesaibot.app.ai.TimeframeHistory

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

        val input = IndicatorInput.from(
            history,
            exchangeTimeMs
        ) ?: return null

        val closes = input.closes

        return MovingAverageSnapshot(
            symbol = input.symbol,
            timeframe = input.timeframe,
            closedAtMs = input.closedAtMs,
            candleCount = input.candleCount,
            close = input.close,

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
