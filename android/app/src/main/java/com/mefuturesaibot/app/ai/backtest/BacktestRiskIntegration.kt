package com.mefuturesaibot.app.ai.backtest

import com.mefuturesaibot.app.ai.risk.RiskPolicy

// Planned economics at the simulated entry; never size using realized loss.
internal object BacktestRiskIntegration {
    fun plannedRisk(side: BacktestSide, entry: Double, stop: Double, target: Double,
        fee: Double, slippage: Double, fundingReserveFraction: Double,
        policy: RiskPolicy): Double? {
        if (listOf(entry,stop,target).any { !it.isFinite() || it <= 0 } ||
            fee !in 0.0..0.01 || slippage !in 0.0..0.01 ||
            !fundingReserveFraction.isFinite() || fundingReserveFraction < 0) return null
        val long = side == BacktestSide.LONG
        if (!(if (long) stop < entry && target > entry else target < entry && stop > entry)) return null
        val stopFill = stop*(if (long) 1-slippage else 1+slippage)
        val targetFill = target*(if (long) 1-slippage else 1+slippage)
        val loss = (if (long) entry-stopFill else stopFill-entry)/entry +
            fee*(1+stopFill/entry)+fundingReserveFraction
        val reward = (if (long) targetFill-entry else entry-targetFill)/entry -
            fee*(1+targetFill/entry)-fundingReserveFraction
        return loss.takeIf { it.isFinite() && it > 0 && reward.isFinite() &&
            reward/it >= policy.minimumNetRewardRisk }
    }
    fun simulate(initial: Double, policy: RiskPolicy, trades: List<CapitalTrade>): List<CapitalOutcome> =
        BacktestCapitalSimulation.run(initial,policy.riskFraction,policy.leverageCap,trades)
}
