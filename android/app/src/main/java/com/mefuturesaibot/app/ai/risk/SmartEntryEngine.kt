package com.mefuturesaibot.app.ai.risk

import com.mefuturesaibot.app.ai.decision.MasterDecision
import com.mefuturesaibot.app.ai.decision.MasterDirection
import com.mefuturesaibot.app.ai.decision.DecisionReason

internal data class EntryCandidate(val side: TradeSide, val price: Double)

internal object SmartEntryEngine {
    // Preliminary quote-based candidate, not an executable order.
    fun candidate(decision: MasterDecision, bid: Double, ask: Double,
                  observedAtMs: Long, nowMs: Long, maxSpreadFraction: Double): EntryCandidate? {
        if (decision.reason != DecisionReason.AGREEMENT || decision.confirmations < 5 ||
            observedAtMs <= 0 || nowMs < observedAtMs || nowMs-observedAtMs >= 2000L ||
            !bid.isFinite() || !ask.isFinite() || bid <= 0 || ask < bid ||
            !maxSpreadFraction.isFinite() || maxSpreadFraction <= 0 || maxSpreadFraction > 0.01 ||
            (ask-bid)/bid > maxSpreadFraction) return null
        return when (decision.direction) {
            MasterDirection.LONG -> EntryCandidate(TradeSide.LONG,ask)
            MasterDirection.SHORT -> EntryCandidate(TradeSide.SHORT,bid)
            MasterDirection.WAIT -> null
        }
    }
}
