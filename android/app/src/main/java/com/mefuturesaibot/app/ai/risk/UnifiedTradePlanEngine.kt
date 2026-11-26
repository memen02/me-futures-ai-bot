package com.mefuturesaibot.app.ai.risk

import java.math.BigDecimal

internal object UnifiedTradePlanEngine {
    fun calculate(entry: EntryCandidate, swing: Double, atr: Double, marketTarget: Double,
                  stopCosts: Double, targetCosts: Double, equity: Double, allocatedMargin: Double,
                  step: BigDecimal, minimum: BigDecimal, maximum: BigDecimal,
                  minNotional: BigDecimal, exchangeCap: Int, policy: RiskPolicy): UnifiedTradePlan? {
        val stop = SmartStopLossEngine.calculate(entry,swing,atr) ?: return null
        val target = SmartTakeProfitEngine.calculate(entry,stop,marketTarget,stopCosts,targetCosts,policy) ?: return null
        val economics = TradeEconomicsEngine.calculate(entry.side,entry.price,stop,target,stopCosts,targetCosts) ?: return null
        val cap = minOf(exchangeCap,policy.leverageCap)
        if (cap < 1) return null
        val raw = PositionRiskSizer.maximumQuantity(equity,allocatedMargin,entry.price,economics,
            policy.copy(leverageCap=cap)) ?: return null
        val quantity = ExchangeQuantityFilter.roundDown(BigDecimal.valueOf(raw),step,minimum,maximum,
            BigDecimal.valueOf(entry.price),minNotional) ?: return null
        val leverage = SmartLeverageEngine.select(quantity.toDouble()*entry.price,allocatedMargin,cap,policy) ?: return null
        return UnifiedTradePlan(entry,stop,target,quantity,leverage,economics)
    }
}
