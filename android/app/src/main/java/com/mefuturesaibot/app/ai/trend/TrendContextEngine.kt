package com.mefuturesaibot.app.ai.trend

import com.mefuturesaibot.app.ai.TimeframeHistory
import com.mefuturesaibot.app.ai.indicators.IndicatorSnapshot
import com.mefuturesaibot.app.ai.indicators.IndicatorSnapshotEngine
import com.mefuturesaibot.app.ai.indicators.AtrEngine

internal data class TrendContext(
    val indicators: IndicatorSnapshot,
    val trend: TrendAssessment,
    val structure: StructureTrend,
    val breakout: StructureBreak?,
    val sweep: LiquiditySweep?,
    val failedBreakout: FailedBreakout?,
    val relativeVolume: Double?,
    val volatility: VolatilityRegime,
    val event: TrendEvent
)

internal object TrendContextEngine {
    fun calculate(history: TimeframeHistory, exchangeTimeMs: Long): TrendContext? {
        val stable = history.copy(candles = history.candles.toList())
        val indicators = IndicatorSnapshotEngine.calculate(stable,exchangeTimeMs) ?: return null
        val trend = TrendEngine.assess(indicators)
        val swings = MarketStructureEngine.swings(stable,exchangeTimeMs) ?: return null
        val structure = StructureTrendEngine.assess(swings,exchangeTimeMs)
        val breakout = StructureBreakEngine.detect(stable,exchangeTimeMs)
        val sweep = LiquiditySweepEngine.detect(stable,exchangeTimeMs)
        val failed = FailedBreakoutEngine.detect(stable,exchangeTimeMs)
        val volume = RelativeVolumeEngine.calculate(stable,exchangeTimeMs)
        val baseline = if (stable.candles.size > 15) {
            val last = stable.candles.last()
            val prior = stable.copy(candles = stable.candles.dropLast(1), observedAt = last.openTime)
            AtrEngine.calculate(prior,last.openTime)?.div(prior.candles.last().close.toDouble())
        } else null
        val volatility = baseline?.let { VolatilityRegimeEngine.assess(indicators,it) } ?: VolatilityRegime.UNKNOWN
        val event = if (failed != null) TrendEvent.WAIT else {
            val raw = TrendEventEngine.assess(trend,structure,breakout,sweep)
            if (raw == TrendEvent.WAIT && breakout == null && sweep == null && PullbackEngine.candidate(indicators,structure))
                TrendEvent.PULLBACK_CANDIDATE else raw
        }
        return TrendContext(indicators,trend,structure,breakout,sweep,failed,volume,volatility,event)
    }
}
