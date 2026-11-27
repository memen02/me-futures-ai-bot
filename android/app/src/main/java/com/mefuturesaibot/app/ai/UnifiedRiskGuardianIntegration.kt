package com.mefuturesaibot.app.ai

import com.mefuturesaibot.app.ai.risk.*
import com.mefuturesaibot.app.ai.decision.MasterDirection

// Preliminary research gate only. No account ownership or execution authority.
internal object UnifiedRiskGuardianIntegration {
    fun permits(assessment: UnifiedMasterAssessment, plan: UnifiedTradePlan,
        equity: Double, existingRisk: Double?, dailyLoss: Double?, liquidation: Double?,
        policy: RiskPolicy, earliest: Long, latest: Long): Boolean {
        val decision = IndependentConfirmationIntegration.revalidate(assessment,earliest,latest)
        val side = when (decision.direction) {
            MasterDirection.LONG -> TradeSide.LONG
            MasterDirection.SHORT -> TradeSide.SHORT
            MasterDirection.WAIT -> return false
        }
        if (plan.entry.side != side || !equity.isFinite() || equity <= 0 ||
            listOf(plan.entry.price,plan.stop,plan.target).any { !it.isFinite() || it <= 0 }) return false
        if (side == TradeSide.LONG && !(plan.stop < plan.entry.price && plan.entry.price < plan.target)) return false
        if (side == TradeSide.SHORT && !(plan.target < plan.entry.price && plan.entry.price < plan.stop)) return false
        if (plan.economics.lossPerUnit < kotlin.math.abs(plan.entry.price-plan.stop)) return false
        return FinalRiskGuardian.permits(plan,equity,existingRisk,dailyLoss,liquidation,policy)
    }
}
