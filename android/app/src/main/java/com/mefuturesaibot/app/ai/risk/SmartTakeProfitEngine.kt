package com.mefuturesaibot.app.ai.risk

internal object SmartTakeProfitEngine {
    // Costs must conservatively cover the proposed entry and exit.
    // Market target is a confirmed structural limit, not a price prediction.
    fun calculate(entry: EntryCandidate, stop: Double, marketTarget: Double,
                  stopCosts: Double, targetCosts: Double, policy: RiskPolicy): Double? {
        val economics = TradeEconomicsEngine.calculate(entry.side,entry.price,stop,marketTarget,
            stopCosts,targetCosts) ?: return null
        return marketTarget.takeIf { economics.rewardRisk >= policy.minimumNetRewardRisk }
    }
}
