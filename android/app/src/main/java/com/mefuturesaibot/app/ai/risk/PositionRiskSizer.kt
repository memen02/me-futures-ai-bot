package com.mefuturesaibot.app.ai.risk

internal object PositionRiskSizer {
    // Preliminary upper bound only. Exchange filters, margin tiers, liquidation
    // distance and existing exposure must be checked before any execution.
    fun maximumQuantity(equity: Double, availableMargin: Double, entry: Double,
                        economics: NetTradeEconomics, policy: RiskPolicy): Double? {
        if (listOf(equity,availableMargin,entry,economics.lossPerUnit,economics.rewardPerUnit)
                .any { !it.isFinite() || it <= 0 }) return null
        if (!economics.rewardRisk.isFinite() || economics.rewardRisk < policy.minimumNetRewardRisk) return null
        val budget = equity * policy.riskFraction
        val riskQuantity = budget / economics.lossPerUnit
        val marginQuantity = availableMargin / entry * policy.leverageCap
        val quantity = minOf(riskQuantity,marginQuantity)
        return quantity.takeIf { it.isFinite() && it > 0 }
    }
}
