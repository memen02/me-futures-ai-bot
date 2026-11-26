package com.mefuturesaibot.app.ai.risk

internal enum class TradeSide { LONG, SHORT }

internal data class NetTradeEconomics(val lossPerUnit: Double, val rewardPerUnit: Double) {
    val rewardRisk: Double get() = rewardPerUnit / lossPerUnit
}

internal object TradeEconomicsEngine {
    // Costs are conservative absolute quote currency estimates per unit,
    // including entry and respective exit fees, slippage and funding.
    fun calculate(side: TradeSide, entry: Double, stop: Double, target: Double,
                  stopCosts: Double, targetCosts: Double): NetTradeEconomics? {
        if (listOf(entry,stop,target).any { !it.isFinite() || it <= 0 } ||
            listOf(stopCosts,targetCosts).any { !it.isFinite() || it < 0 }) return null
        val loss = if (side == TradeSide.LONG) entry-stop else stop-entry
        val reward = if (side == TradeSide.LONG) target-entry else entry-target
        if (loss <= 0 || reward <= 0) return null
        val netLoss = loss+stopCosts
        val netReward = reward-targetCosts
        if (!netLoss.isFinite() || !netReward.isFinite() || netReward <= 0 ||
            !(netReward/netLoss).isFinite()) return null
        return NetTradeEconomics(netLoss,netReward)
    }
}
