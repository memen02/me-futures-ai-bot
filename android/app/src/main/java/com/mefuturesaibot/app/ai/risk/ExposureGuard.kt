package com.mefuturesaibot.app.ai.risk

internal object ExposureGuard {
    // Inputs include pending entries and all open positions, not only filled orders.
    // Unknown exposure must be represented by null.
    fun permits(equity: Double, existingRisk: Double?, proposedRisk: Double,
                dailyLoss: Double?, aggregateRiskFraction: Double = 0.02,
                dailyLossFraction: Double = 0.03): Boolean {
        val open = existingRisk ?: return false
        val loss = dailyLoss ?: return false
        if (listOf(equity,open,proposedRisk,loss,aggregateRiskFraction,dailyLossFraction)
                .any { !it.isFinite() } || equity <= 0 || open < 0 || proposedRisk <= 0 || loss < 0 ||
            aggregateRiskFraction !in 0.005..0.02 || dailyLossFraction !in 0.005..0.03) return false
        return proposedRisk <= equity*0.01 &&
            open+proposedRisk <= equity*aggregateRiskFraction &&
            loss < equity*dailyLossFraction &&
            loss+open+proposedRisk <= equity*dailyLossFraction
    }
}
