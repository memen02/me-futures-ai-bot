package com.mefuturesaibot.app.ai.learning

// Eligibility for further offline review only, never live promotion.
internal object LearningSafetyGuardian {
    fun eligibleForReview(comparison: ShadowComparison?, drift: DriftStatus,
                          evaluationStartMs: Long, evaluationEndMs: Long): Boolean {
        if (comparison == null || drift != DriftStatus.STABLE ||
            evaluationStartMs <= 0 || evaluationEndMs <= evaluationStartMs ||
            evaluationEndMs-evaluationStartMs < 30L*86400000L) return false
        return comparison.count in 100..10000 &&
            listOf(comparison.baselineMeanR,comparison.candidateMeanR,comparison.meanImprovement).all { it.isFinite() } &&
            comparison.candidateMeanR > 0 && comparison.meanImprovement >= 0.1 &&
            kotlin.math.abs(comparison.meanImprovement-(comparison.candidateMeanR-comparison.baselineMeanR)) < 1e-9
    }
}
