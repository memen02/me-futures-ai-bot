package com.mefuturesaibot.app.ai.indicators

import com.mefuturesaibot.app.ai.Timeframe
import com.mefuturesaibot.app.ai.TimeframeHistory

internal data class IndicatorSnapshot(
    val symbol: String,
    val timeframe: Timeframe,
    val closedAtMs: Long,
    val candleCount: Int,
    val close: Double,
    val macd: MacdPoint?,
    val ema20: Double?,
    val ema50: Double?,
    val rsi14: Double?,
    val bollinger: BollingerBands?,
    val roc12: Double?,
    val wma20: Double?,
    val atr14: Double?,
    val stochasticRsi: Double?,
    val rollingVwap: Double?,
    val directionalStrength: DirectionalStrength?
)

internal object IndicatorSnapshotEngine {
    fun calculate(history: TimeframeHistory, exchangeTimeMs: Long): IndicatorSnapshot? {
        val stable = history.copy(candles = history.candles.toList())
        val input = IndicatorInput.from(stable, exchangeTimeMs) ?: return null
        return IndicatorSnapshot(
            input.symbol, input.timeframe, input.closedAtMs, input.candleCount,
            input.close,
            MacdEngine.series(input.closes)?.lastOrNull(),
            IndicatorMath.ema(input.closes, 20),
            IndicatorMath.ema(input.closes, 50),
            RsiEngine.series(input.closes)?.lastOrNull(),
            BollingerEngine.bands(input.closes),
            RocEngine.value(input.closes),
            WeightedMovingAverageEngine.value(input.closes),
            AtrEngine.calculate(stable, exchangeTimeMs),
            StochasticRsiEngine.value(input.closes),
            RollingVwapEngine.calculate(stable, exchangeTimeMs),
            AdxEngine.calculate(stable, exchangeTimeMs)
        )
    }
}
