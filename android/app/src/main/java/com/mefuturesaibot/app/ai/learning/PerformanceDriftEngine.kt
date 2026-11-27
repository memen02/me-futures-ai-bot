package com.mefuturesaibot.app.ai.learning

internal enum class DriftStatus { INSUFFICIENT, STABLE, WARNING }

internal object PerformanceDriftEngine {
    // Warning threshold only; not a statistical significance test.
    fun assess(baseline: List<TradeExperience>, recent: List<TradeExperience>,
               datasetId: String, strategyVersion: String, asOfMs: Long): DriftStatus {
        if (baseline.size !in 100..10000 || recent.size !in 100..10000) return DriftStatus.INSUFFICIENT
        val old = ExperienceEvaluator.evaluate(baseline,datasetId,strategyVersion,asOfMs)
            ?: return DriftStatus.INSUFFICIENT
        val new = ExperienceEvaluator.evaluate(recent,datasetId,strategyVersion,asOfMs)
            ?: return DriftStatus.INSUFFICIENT
        if (baseline.maxOf { it.closedAtMs } >= recent.minOf { it.openedAtMs } ||
            baseline.map { it.tradeId }.toSet().intersect(recent.map { it.tradeId }.toSet()).isNotEmpty())
            return DriftStatus.INSUFFICIENT
        val decline = old.meanNetR-new.meanNetR
        if (!decline.isFinite()) return DriftStatus.INSUFFICIENT
        return if (new.meanNetR <= 0 || decline >= 0.25 || old.winRate-new.winRate >= 0.10)
            DriftStatus.WARNING else DriftStatus.STABLE
    }
}
