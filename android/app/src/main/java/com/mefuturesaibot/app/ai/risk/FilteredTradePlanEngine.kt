package com.mefuturesaibot.app.ai.risk

import java.math.BigDecimal

internal object FilteredTradePlanEngine {
    // Entry is a conservative price estimate, not a guaranteed fill.
    // Costs must bound fees, slippage and funding at the rounded prices.
    fun calculate(entry: EntryCandidate, swing: Double, atr: Double, marketTarget: Double,
                  stopCosts: Double, targetCosts: Double, equity: Double, margin: Double,
                  tick: BigDecimal, minPrice: BigDecimal, maxPrice: BigDecimal,
                  step: BigDecimal, minQty: BigDecimal, maxQty: BigDecimal,
                  minNotional: BigDecimal, exchangeCap: Int, policy: RiskPolicy): UnifiedTradePlan? {
        if (!entry.price.isFinite() || !marketTarget.isFinite()) return null
        val long = entry.side == TradeSide.LONG
        fun round(value: Double, up: Boolean): Double? =
            ExchangePriceFilter.round(BigDecimal.valueOf(value),tick,minPrice,maxPrice,up)
                ?.toDouble()?.takeIf { it.isFinite() && it > 0 }
        val price = round(entry.price,long) ?: return null
        val adjusted = EntryCandidate(entry.side,price)
        val rawStop = SmartStopLossEngine.calculate(adjusted,swing,atr) ?: return null
        val stop = round(rawStop,!long) ?: return null
        val target = round(marketTarget,!long) ?: return null
        val economics = TradeEconomicsEngine.calculate(entry.side,price,stop,target,stopCosts,targetCosts) ?: return null
        val cap = minOf(exchangeCap,policy.leverageCap)
        if (cap < 1) return null
        val rawQty = PositionRiskSizer.maximumQuantity(equity,margin,price,economics,
            policy.copy(leverageCap=cap)) ?: return null
        val quantity = ExchangeQuantityFilter.roundDown(BigDecimal.valueOf(rawQty),step,minQty,maxQty,
            BigDecimal.valueOf(price),minNotional) ?: return null
        val leverage = SmartLeverageEngine.select(quantity.toDouble()*price,margin,cap,policy) ?: return null
        if (quantity.toDouble()*economics.lossPerUnit > equity*policy.riskFraction) return null
        return UnifiedTradePlan(adjusted,stop,target,quantity,leverage,economics)
    }
}
