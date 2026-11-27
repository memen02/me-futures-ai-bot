package com.mefuturesaibot.app.ai.backtest

internal data class HistoricalBar(val openTime: Long, val open: Double,
    val high: Double, val low: Double, val close: Double, val volume: Double)

internal class HistoricalMarketData(val symbol: String, val intervalMs: Long,
    bars: List<HistoricalBar>) {
    val bars: List<HistoricalBar> = java.util.Collections.unmodifiableList(ArrayList(bars))
    init {
        require(symbol.matches(Regex("[A-Z0-9]{1,32}")))
        require(intervalMs in 60000L..604800000L)
        require(bars.size in 2..100000)
        require(HistoricalDataIntegrity.valid(this.bars, intervalMs))
    }
    // Only candles fully closed at the decision timestamp are visible.
    fun closedAt(timestamp: Long): List<HistoricalBar> =
        bars.takeWhile { it.openTime <= timestamp && timestamp - it.openTime >= intervalMs }
}
