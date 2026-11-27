package com.mefuturesaibot.app.ai.backtest

internal data class ResearchBacktestReport(val symbol: String, val strategyVersion: String,
    val startMs: Long, val endMs: Long, val result: UnifiedBacktestResult,
    val researchOnly: Boolean = true)

internal object BacktestReportEngine {
    // Metadata is caller supplied; not a certification of data provenance or out-of-sample testing.
    fun create(data: HistoricalMarketData, version: String, result: UnifiedBacktestResult): ResearchBacktestReport {
        require(version.matches(Regex("[A-Za-z0-9._-]{1,128}")))
        require(result.executedTrades == result.performance.trades &&
            result.executedTrades in 0..result.simulatedTrades && result.simulatedTrades < data.bars.size)
        return ResearchBacktestReport(data.symbol,version,data.bars.first().openTime,
            data.bars.last().openTime+data.intervalMs,result)
    }
}
