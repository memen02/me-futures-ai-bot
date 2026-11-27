package com.mefuturesaibot.app.ai.backtest

import com.mefuturesaibot.app.ai.risk.RiskPolicy

internal data class UnifiedBacktestResult(val simulatedTrades: Int, val executedTrades: Int,
    val performance: BacktestPerformance)

internal object UnifiedBacktestCoordinator {
    // Offline research only. Funding completeness and strategy provenance are caller obligations.
    // Bounded workload; no network, no order execution, no automatic promotion.
    fun run(data: HistoricalMarketData, initial: Double, policy: RiskPolicy,
        fee: Double, slippage: Double, fundingReserve: Double, funding: List<FundingObservation>,
        strategy: (List<HistoricalBar>) -> BacktestSignal?): UnifiedBacktestResult {
        require(data.bars.size <= 1500 && funding.size <= 1500)
        require(initial.isFinite() && initial > 0 && fundingReserve.isFinite() && fundingReserve >= 0)
        val events = funding.toList()
        require(events.zipWithNext().all { (a,b) -> a.timestamp < b.timestamp })
        require(events.all { it.timestamp >= 0 && it.rate.isFinite() && it.mark.isFinite() && it.mark > 0 })
        val trades = BacktestSimulationEngine.runFiltered(data,fee,slippage,{ signal,entry ->
            BacktestRiskIntegration.plannedRisk(signal.side,entry,signal.stop,signal.target,
                fee,slippage,fundingReserve,policy)
        },strategy)
        val capital = trades.map {
            CapitalTrade(checkNotNull(it.plannedRisk),BacktestTradingCosts.adjustedReturn(it,events))
        }
        val outcomes = BacktestRiskIntegration.simulate(initial,policy,capital)
        return UnifiedBacktestResult(trades.size,outcomes.size,BacktestPerformanceAnalysis.analyze(initial,outcomes))
    }
}
