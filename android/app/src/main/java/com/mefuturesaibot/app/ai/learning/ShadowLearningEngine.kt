package com.mefuturesaibot.app.ai.learning

internal data class ShadowPair(val opportunityId: String, val baseline: TradeExperience,
                               val candidate: TradeExperience)

internal data class ShadowComparison(val count: Int, val baselineMeanR: Double,
                                     val candidateMeanR: Double, val meanImprovement: Double)

// Descriptive comparison of matched simulated outcomes. No execution or promotion authority.
internal object ShadowLearningEngine {
    fun compare(pairs: List<ShadowPair>, datasetId: String, baselineVersion: String,
                candidateVersion: String, evaluationStartMs: Long, asOfMs: Long): ShadowComparison? {
        if (!datasetId.startsWith("shadow:") || baselineVersion == candidateVersion ||
            evaluationStartMs <= 0 || asOfMs <= evaluationStartMs || pairs.size !in 100..10000 ||
            pairs.map { it.opportunityId }.toSet().size != pairs.size ||
            pairs.any { it.opportunityId.isBlank() || it.baseline.symbol != it.candidate.symbol ||
                it.baseline.openedAtMs != it.candidate.openedAtMs ||
                it.baseline.openedAtMs < evaluationStartMs }) return null
        val baseline = ExperienceEvaluator.evaluate(pairs.map { it.baseline },datasetId,baselineVersion,asOfMs)
            ?: return null
        val candidate = ExperienceEvaluator.evaluate(pairs.map { it.candidate },datasetId,candidateVersion,asOfMs)
            ?: return null
        val improvement = candidate.meanNetR-baseline.meanNetR
        if (!improvement.isFinite()) return null
        return ShadowComparison(pairs.size,baseline.meanNetR,candidate.meanNetR,improvement)
    }
}
