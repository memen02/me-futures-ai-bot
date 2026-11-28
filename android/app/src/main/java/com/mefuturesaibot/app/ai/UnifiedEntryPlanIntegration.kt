package com.mefuturesaibot.app.ai

import com.mefuturesaibot.app.ai.risk.*
import java.math.BigDecimal

internal data class ResearchEntryInputs(val symbol: String, val bid: Double, val ask: Double,
    val quoteAtMs: Long, val maxSpreadFraction: Double, val swing: Double, val atr: Double,
    val target: Double, val stopCosts: Double, val targetCosts: Double,
    val equity: Double, val margin: Double, val existingRisk: Double?, val dailyLoss: Double?,
    val liquidation: Double?, val tick: BigDecimal, val minPrice: BigDecimal, val maxPrice: BigDecimal,
    val step: BigDecimal, val minQty: BigDecimal, val maxQty: BigDecimal,
    val minNotional: BigDecimal, val exchangeCap: Int)

// Research only. Caller must bind quote, account, filters and liquidation model
// to the same symbol, venue and proposed position. Reconcile before execution.
internal object UnifiedEntryPlanIntegration {
    fun calculate(source: UnifiedTimeframeSnapshot, assessment: UnifiedMasterAssessment,
        input: ResearchEntryInputs, policy: RiskPolicy, earliest: Long, latest: Long): UnifiedTradePlan? {
        if (assessment.source !== source || input.symbol != source.symbol || !source.fresh(earliest,latest) ||
            input.quoteAtMs > earliest || input.exchangeCap < 1 ||
            listOf(input.stopCosts,input.targetCosts).any { !it.isFinite() || it < 0 }) return null
        val decision = IndependentConfirmationIntegration.revalidate(assessment,earliest,latest)
        val entry = SmartEntryEngine.candidate(decision,input.bid,input.ask,input.quoteAtMs,latest,
            input.maxSpreadFraction) ?: return null
        val plan = FilteredTradePlanEngine.calculate(entry,input.swing,input.atr,input.target,
            input.stopCosts,input.targetCosts,input.equity,input.margin,input.tick,input.minPrice,
            input.maxPrice,input.step,input.minQty,input.maxQty,input.minNotional,input.exchangeCap,policy)
            ?: return null
        return plan.takeIf { UnifiedRiskGuardianIntegration.permits(assessment,it,input.equity,
            input.existingRisk,input.dailyLoss,input.liquidation,policy,earliest,latest) }
    }
}
