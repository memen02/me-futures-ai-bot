package com.mefuturesaibot.app.ai.backtest

internal enum class ResearchVerdict { INSUFFICIENT, BELOW_TARGET, RESEARCH_CANDIDATE }

internal object BacktestStrategyEvaluation {
    // Advisory only: never authorizes live trading or strategy promotion.
    fun assess(report: BacktestPerformance, minimumTrades: Int = 100,
        targetWinRate: Double = 0.8, maximumDrawdown: Double = 0.1): ResearchVerdict {
        require(minimumTrades >= 100 && targetWinRate in 0.8..1.0 && maximumDrawdown > 0 && maximumDrawdown <= 0.1)
        if (report.trades < minimumTrades || report.wins < 0 || report.losses < 0 ||
            report.breakeven < 0 || report.wins.toLong()+report.losses+report.breakeven != report.trades.toLong())
            return ResearchVerdict.INSUFFICIENT
        val rate = report.winRate ?: return ResearchVerdict.INSUFFICIENT
        val factor = report.profitFactor ?: return ResearchVerdict.INSUFFICIENT
        if (!rate.isFinite() || rate != report.wins.toDouble()/report.trades ||
            !factor.isFinite() || !report.netPnl.isFinite() ||
            !report.maxClosedEquityDrawdown.isFinite() || report.maxClosedEquityDrawdown < 0)
            return ResearchVerdict.INSUFFICIENT
        return if (rate >= targetWinRate && factor > 1 && report.netPnl > 0 &&
            report.maxClosedEquityDrawdown <= maximumDrawdown) ResearchVerdict.RESEARCH_CANDIDATE
        else ResearchVerdict.BELOW_TARGET
    }
}
