package com.mefuturesaibot.app.ai.learning

import kotlin.math.sqrt

internal data class ConfidenceEstimate(val samples: Int, val observedWinRate: Double,
                                       val lowerBound: Double, val upperBound: Double)

internal object ConfidenceCalibrationEngine {
    // Empirical outcome interval for a predeclared score bin, not a live probability.
    // Wilson interval assumes independent Bernoulli observations.
    fun estimate(trades: List<TradeExperience>, datasetId: String, strategyVersion: String,
                 asOfMs: Long): ConfidenceEstimate? {
        val stats = ExperienceEvaluator.evaluate(trades,datasetId,strategyVersion,asOfMs) ?: return null
        if (stats.count < 100) return null
        val n = stats.count.toDouble()
        val p = stats.winRate
        val z = 1.96
        val denominator = 1+z*z/n
        val center = (p+z*z/(2*n))/denominator
        val half = z*sqrt(p*(1-p)/n+z*z/(4*n*n))/denominator
        return ConfidenceEstimate(stats.count,p,(center-half).coerceIn(0.0,1.0),
            (center+half).coerceIn(0.0,1.0))
    }
}
