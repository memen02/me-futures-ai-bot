package com.mefuturesaibot.app.ai.risk

internal data class RiskPolicy(
    val riskFraction: Double = 0.005,
    val leverageCap: Int = 20,
    val minimumNetRewardRisk: Double = 2.0
) {
    init {
        require(riskFraction.isFinite() && riskFraction in 0.005..0.01)
        require(leverageCap in 1..20)
        require(minimumNetRewardRisk.isFinite() && minimumNetRewardRisk >= 2.0)
    }
}
