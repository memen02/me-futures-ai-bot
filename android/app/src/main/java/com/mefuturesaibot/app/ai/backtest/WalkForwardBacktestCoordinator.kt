package com.mefuturesaibot.app.ai.backtest

import com.mefuturesaibot.app.ai.risk.RiskPolicy

internal data class WalkForwardResearchResult(val trainingEndMs: Long, val evaluationStartMs: Long,
    val evaluationEndMs: Long, val result: UnifiedBacktestResult)

// Independent capital per fold; results must not be treated as a compounded equity curve.
// Trainer must be trusted and return fresh fold-local strategy state without future data.
internal object WalkForwardBacktestCoordinator {
    fun run(data: HistoricalMarketData, trainSize: Int, evaluationSize: Int, embargo: Int,
        initial: Double, policy: RiskPolicy, fee: Double, slippage: Double,
        fundingReserve: Double, funding: List<FundingObservation>,
        train: (List<HistoricalBar>) -> ((List<HistoricalBar>) -> BacktestSignal?)): List<WalkForwardResearchResult> {
        require(data.bars.size <= 1500 && funding.size <= 1500 && embargo >= 1)
        val events = funding.toList()
        require(events.zipWithNext().all { (a,b) -> a.timestamp < b.timestamp })
        require(events.all { it.timestamp >= 0 && it.rate.isFinite() && it.mark.isFinite() && it.mark > 0 })
        require(initial.isFinite() && initial > 0 && fee in 0.0..0.01 && slippage in 0.0..0.01 &&
            fundingReserve.isFinite() && fundingReserve >= 0)
        val folds = WalkForwardValidation.folds(data,trainSize,evaluationSize,embargo)
        require(folds.size in 1..100)
        return folds.map { fold ->
            val trainingEnd = fold.training.last().openTime+data.intervalMs
            val evaluation = HistoricalMarketData(data.symbol,data.intervalMs,fold.evaluation)
            val start = evaluation.bars.first().openTime
            val end = evaluation.bars.last().openTime+data.intervalMs
            check(trainingEnd < start)
            val strategy = train(fold.training)
            val result = UnifiedBacktestCoordinator.run(evaluation,initial,policy,fee,slippage,
                fundingReserve,events.filter { it.timestamp in start..end },strategy)
            WalkForwardResearchResult(trainingEnd,start,end,result)
        }
    }
}
