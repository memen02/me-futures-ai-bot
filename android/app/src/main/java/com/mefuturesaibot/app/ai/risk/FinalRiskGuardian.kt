package com.mefuturesaibot.app.ai.risk

internal object FinalRiskGuardian {
    // Preliminary validation only. Venue-specific reconciliation, fresh quotes,
    // price tick filters and atomic exposure reservation remain mandatory.
    fun permits(plan: UnifiedTradePlan, equity: Double, existingRisk: Double?,
                dailyLoss: Double?, liquidation: Double?, policy: RiskPolicy): Boolean {
        val q = plan.quantity.toDouble()
        if (!q.isFinite() || q <= 0 || plan.leverage !in 1..policy.leverageCap) return false
        val economics = plan.economics
        if (!economics.lossPerUnit.isFinite() || economics.lossPerUnit <= 0 ||
            !economics.rewardRisk.isFinite() || economics.rewardRisk < policy.minimumNetRewardRisk) return false
        val risk = q*economics.lossPerUnit
        if (!risk.isFinite() || risk > equity*policy.riskFraction) return false
        return ExposureGuard.permits(equity,existingRisk,risk,dailyLoss) &&
            LiquidationProtection.permits(plan.entry.side,plan.entry.price,plan.stop,liquidation)
    }
}
