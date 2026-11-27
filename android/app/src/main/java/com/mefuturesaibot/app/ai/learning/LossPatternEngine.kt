package com.mefuturesaibot.app.ai.learning

internal data class LossPattern(val symbol: String, val stats: ExperienceStats)

internal object LossPatternEngine {
    // Exploratory warning only; no automatic strategy changes.
    fun detect(trades: List<TradeExperience>, datasetId: String, strategyVersion: String,
               asOfMs: Long, minimumSamples: Int = 30): List<LossPattern>? {
        if (minimumSamples !in 30..10000) return null
        ExperienceEvaluator.evaluate(trades,datasetId,strategyVersion,asOfMs) ?: return null
        return trades.groupBy { it.symbol }.mapNotNull { (symbol, sample) ->
            if (sample.size < minimumSamples) return@mapNotNull null
            val stats = ExperienceEvaluator.evaluate(sample,datasetId,strategyVersion,asOfMs)
                ?: return@mapNotNull null
            if (stats.meanNetR < 0) LossPattern(symbol,stats) else null
        }.sortedBy { it.symbol }
    }
}
