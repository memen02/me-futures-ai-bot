package com.mefuturesaibot.app.ai.learning

internal data class ExperienceStats(val count: Int, val wins: Int, val losses: Int,
                                    val meanNetR: Double, val winRate: Double)

internal object ExperienceEvaluator {
    // Descriptive statistics only, not a calibrated prediction.
    fun evaluate(trades: List<TradeExperience>, datasetId: String,
                 strategyVersion: String, asOfMs: Long): ExperienceStats? {
        if (trades.isEmpty() || trades.size > 10000 || asOfMs <= 0 ||
            trades.any { !it.valid() || it.datasetId != datasetId ||
                it.strategyVersion != strategyVersion || it.closedAtMs > asOfMs } ||
            trades.map { it.tradeId }.toSet().size != trades.size) return null
        val wins = trades.count { it.netPnl > 0 }
        val losses = trades.count { it.netPnl < 0 }
        val mean = trades.sumOf { it.netR / trades.size }
        if (!mean.isFinite()) return null
        return ExperienceStats(trades.size,wins,losses,mean,wins.toDouble()/trades.size)
    }
}
