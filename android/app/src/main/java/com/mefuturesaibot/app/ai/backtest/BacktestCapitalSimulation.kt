package com.mefuturesaibot.app.ai.backtest

// Research only. Inputs must include estimated stop loss costs and net realized return.
internal data class CapitalTrade(val riskDistanceFraction: Double, val netReturn: Double)
internal data class CapitalOutcome(val before: Double, val notional: Double, val pnl: Double, val after: Double)

internal object BacktestCapitalSimulation {
    fun run(initial: Double, riskFraction: Double, leverageCap: Int,
        trades: List<CapitalTrade>): List<CapitalOutcome> {
        require(initial.isFinite() && initial > 0)
        require(riskFraction in 0.005..0.01 && leverageCap in 1..20)
        require(trades.size <= 100000)
        var equity = initial
        val outcomes = mutableListOf<CapitalOutcome>()
        for (trade in trades) {
            require(trade.riskDistanceFraction.isFinite() && trade.riskDistanceFraction > 0 && trade.netReturn.isFinite())
            if (equity <= 0) break
            val notional = minOf(equity * riskFraction / trade.riskDistanceFraction, equity * leverageCap)
            val pnl = notional * trade.netReturn
            val after = equity + pnl
            check(notional.isFinite() && pnl.isFinite() && after.isFinite())
            outcomes.add(CapitalOutcome(equity,notional,pnl,after))
            equity = after
        }
        return outcomes
    }
}
