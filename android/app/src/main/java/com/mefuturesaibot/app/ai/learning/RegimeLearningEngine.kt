package com.mefuturesaibot.app.ai.learning

internal enum class LearningRegime { UP_TREND, DOWN_TREND, SIDEWAYS, UNKNOWN }

// Regime must be captured at entry, never inferred from the later outcome.
internal data class RegimeExperience(val trade: TradeExperience, val regime: LearningRegime,
                                     val regimeObservedAtMs: Long)

internal object RegimeLearningEngine {
    fun evaluate(records: List<RegimeExperience>, datasetId: String, strategyVersion: String,
                 asOfMs: Long, minimumSamples: Int = 30): Map<LearningRegime, ExperienceStats>? {
        if (minimumSamples !in 30..10000 || records.size > 10000 ||
            records.any { it.regimeObservedAtMs <= 0 || it.regimeObservedAtMs > it.trade.openedAtMs }) return null
        ExperienceEvaluator.evaluate(records.map { it.trade },datasetId,strategyVersion,asOfMs) ?: return null
        val result = mutableMapOf<LearningRegime, ExperienceStats>()
        for ((regime, sample) in records.groupBy { it.regime }) {
            if (regime == LearningRegime.UNKNOWN || sample.size < minimumSamples) continue
            result[regime] = ExperienceEvaluator.evaluate(sample.map { it.trade },datasetId,strategyVersion,asOfMs)
                ?: return null
        }
        return result.toMap()
    }
}
