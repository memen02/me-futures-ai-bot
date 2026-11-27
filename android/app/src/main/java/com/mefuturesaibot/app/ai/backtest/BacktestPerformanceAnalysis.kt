package com.mefuturesaibot.app.ai.backtest

internal data class BacktestPerformance(val trades: Int, val wins: Int, val losses: Int,
    val breakeven: Int, val winRate: Double?, val profitFactor: Double?,
    val netPnl: Double, val maxClosedEquityDrawdown: Double)

internal object BacktestPerformanceAnalysis {
    fun analyze(initial: Double, outcomes: List<CapitalOutcome>): BacktestPerformance {
        require(initial.isFinite() && initial > 0 && outcomes.size <= 100000)
        var equity = initial
        var peak = initial
        var drawdown = 0.0
        var gains = 0.0
        var losses = 0.0
        var wins = 0
        var lost = 0
        for (o in outcomes) {
            require(equity > 0 && o.before == equity && o.notional.isFinite() && o.notional > 0)
            require(o.pnl.isFinite() && o.after.isFinite() && o.after == o.before + o.pnl)
            if (o.pnl > 0) { wins++; gains += o.pnl }
            if (o.pnl < 0) { lost++; losses -= o.pnl }
            equity = o.after
            peak = maxOf(peak,equity)
            drawdown = maxOf(drawdown,(peak-equity)/peak)
        }
        check(gains.isFinite() && losses.isFinite() && drawdown.isFinite())
        return BacktestPerformance(outcomes.size,wins,lost,outcomes.size-wins-lost,
            if (outcomes.isEmpty()) null else wins.toDouble()/outcomes.size,
            if (losses > 0) (gains/losses).takeIf { it.isFinite() } else null,
            equity-initial,drawdown)
    }
}
