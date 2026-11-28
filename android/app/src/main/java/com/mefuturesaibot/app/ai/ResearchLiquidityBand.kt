package com.mefuturesaibot.app.ai

import com.mefuturesaibot.app.ai.decision.LiquiditySnapshot

internal object ResearchLiquidityBand {
    // Caller must supply a complete, synchronized book for the requested band.
    fun calculate(symbol: String, at: Long, bids: List<Pair<Double,Double>>,
        asks: List<Pair<Double,Double>>, synchronizedBook: Boolean): LiquiditySnapshot? {
        if (!synchronizedBook || !UnifiedMarketDataProvider.validSymbol(symbol) || at <= 0 ||
            bids.size !in 2..1000 || asks.size !in 2..1000) return null
        if ((bids+asks).any { (p,q) -> !p.isFinite() || !q.isFinite() || p <= 0 || q <= 0 }) return null
        if (bids.zipWithNext().any { (a,b) -> a.first <= b.first } ||
            asks.zipWithNext().any { (a,b) -> a.first >= b.first }) return null
        val bid = bids.first().first
        val ask = asks.first().first
        if (ask <= bid) return null
        val mid = bid/2+ask/2
        val low = mid*0.999
        val high = mid*1.001
        if (bid < low || ask > high || bids.last().first > low || asks.last().first < high) return null
        val buy = bids.filter { it.first >= low }.sumOf { it.first*it.second }
        val sell = asks.filter { it.first <= high }.sumOf { it.first*it.second }
        if (!buy.isFinite() || !sell.isFinite() || buy <= 0 || sell <= 0) return null
        return LiquiditySnapshot(symbol,at,buy,sell,true)
    }
}
