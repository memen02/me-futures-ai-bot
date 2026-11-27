package com.mefuturesaibot.app.ai.backtest

internal enum class BacktestReleaseDecision { BLOCKED, RESEARCH_REVIEW_ONLY }

internal object BacktestReleaseSafetyGuardian {
    // No backtest result can authorize REAL execution. Even a strong report needs
    // independent provenance, holdout, liquidation, paper and execution safety review.
    fun assess(folds: List<WalkForwardResearchResult>): BacktestReleaseDecision {
        if (folds.size !in 3..100) return BacktestReleaseDecision.BLOCKED
        var previousEnd = 0L
        for (fold in folds) {
            if (fold.trainingEndMs <= 0 || fold.trainingEndMs >= fold.evaluationStartMs ||
                fold.evaluationStartMs < previousEnd || fold.evaluationEndMs <= fold.evaluationStartMs)
                return BacktestReleaseDecision.BLOCKED
            val result = fold.result
            if (result.executedTrades != result.simulatedTrades ||
                result.executedTrades != result.performance.trades ||
                BacktestStrategyEvaluation.assess(result.performance) != ResearchVerdict.RESEARCH_CANDIDATE)
                return BacktestReleaseDecision.BLOCKED
            previousEnd = fold.evaluationEndMs
        }
        return BacktestReleaseDecision.RESEARCH_REVIEW_ONLY
    }
}
