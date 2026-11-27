package com.mefuturesaibot.app.ai.backtest

internal data class WalkForwardFold(val training: List<HistoricalBar>, val evaluation: List<HistoricalBar>)

internal object WalkForwardValidation {
    // Disjoint evaluation windows. Training precedes evaluation with an optional embargo.
    fun folds(data: HistoricalMarketData, trainSize: Int, evaluationSize: Int,
        embargo: Int): List<WalkForwardFold> {
        require(trainSize in 2..100000 && evaluationSize in 2..100000 && embargo in 0..100000)
        val bars = data.bars
        val result = mutableListOf<WalkForwardFold>()
        var start = 0
        while (start + trainSize + embargo + evaluationSize <= bars.size) {
            val end = start + trainSize
            val test = end + embargo
            result.add(WalkForwardFold(bars.subList(start,end),bars.subList(test,test+evaluationSize)))
            start += evaluationSize
        }
        return result
    }
}
