package com.mefuturesaibot.app.ai.learning

internal data class LearningReport(val stats: ExperienceStats, val confidence: ConfidenceEstimate?,
                                   val lossPatterns: List<LossPattern>)

// Read-only evaluation of a bounded snapshot. Run outside the UI thread.
internal object LearningCoordinator {
    fun evaluate(memory: MarketMemory, datasetId: String, version: String, nowMs: Long): LearningReport? {
        val sample = memory.snapshot(version,nowMs)
        val stats = ExperienceEvaluator.evaluate(sample,datasetId,version,nowMs) ?: return null
        val confidence = ConfidenceCalibrationEngine.estimate(sample,datasetId,version,nowMs)
        val losses = LossPatternEngine.detect(sample,datasetId,version,nowMs) ?: return null
        return LearningReport(stats,confidence,losses)
    }
}
