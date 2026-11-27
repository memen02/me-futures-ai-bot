package com.mefuturesaibot.app.ai.learning

// Candidate only: never bypass independent evidence or risk vetoes.
internal object AdaptiveWeightEngine {
    fun propose(current: Double, observations: List<TradeExperience>, datasetId: String,
                strategyVersion: String, asOfMs: Long): Double? {
        if (!current.isFinite() || current !in 0.5..1.5) return null
        val stats = ExperienceEvaluator.evaluate(observations,datasetId,strategyVersion,asOfMs)
            ?: return null
        if (stats.count < 100) return null
        // Caller must supply a predeclared, comparable evidence cohort.
        val delta = (stats.meanNetR.coerceIn(-1.0,1.0)*0.05)
        return (current+delta).coerceIn(0.5,1.5)
    }
}
