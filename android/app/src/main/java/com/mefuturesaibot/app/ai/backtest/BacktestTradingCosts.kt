package com.mefuturesaibot.app.ai.backtest

internal data class FundingObservation(val timestamp: Long, val rate: Double, val mark: Double)

internal object BacktestTradingCosts {
    // netReturn already includes execution fees and slippage. Funding is additional.
    fun adjustedReturn(trade: BacktestTrade, funding: List<FundingObservation>): Double {
        require(trade.entry.isFinite() && trade.entry > 0 && trade.netReturn.isFinite())
        require(funding.size <= 100000)
        require(funding.zipWithNext().all { (a,b) -> a.timestamp < b.timestamp })
        var result = trade.netReturn
        for (event in funding) {
            require(event.timestamp >= 0 && event.rate.isFinite() && event.mark.isFinite() && event.mark > 0)
            if (event.timestamp > trade.entryTime && event.timestamp <= trade.exitTime) {
                result -= (if (trade.side == BacktestSide.LONG) 1 else -1) * event.rate * event.mark / trade.entry
            }
        }
        require(result.isFinite())
        return result
    }
}
