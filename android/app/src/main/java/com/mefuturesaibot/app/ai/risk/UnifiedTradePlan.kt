package com.mefuturesaibot.app.ai.risk

import java.math.BigDecimal

// Preliminary plan shared by both venues. Not an execution authorization.
internal data class UnifiedTradePlan(
    val entry: EntryCandidate,
    val stop: Double,
    val target: Double,
    val quantity: BigDecimal,
    val leverage: Int,
    val economics: NetTradeEconomics
)
