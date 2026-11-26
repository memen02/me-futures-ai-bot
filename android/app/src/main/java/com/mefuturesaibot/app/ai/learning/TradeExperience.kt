package com.mefuturesaibot.app.ai.learning

// Closed, reconciled trade only. Keep DEMO and REAL datasets separate.
internal data class TradeExperience(
    val tradeId: String,
    val datasetId: String,
    val strategyVersion: String,
    val symbol: String,
    val openedAtMs: Long,
    val closedAtMs: Long,
    val grossPnl: Double,
    val fees: Double,
    val fundingPaid: Double,
    val initialRisk: Double
) {
    val netPnl: Double get() = grossPnl-fees-fundingPaid
    val netR: Double get() = netPnl/initialRisk
    fun valid(): Boolean = listOf(tradeId,datasetId,strategyVersion,symbol).all { it.isNotBlank() } &&
        openedAtMs > 0 && closedAtMs > openedAtMs &&
        listOf(grossPnl,fees,fundingPaid,initialRisk,netPnl,netR).all { it.isFinite() } &&
        fees >= 0 && initialRisk > 0
}
