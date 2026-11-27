package com.mefuturesaibot.app.ai

import com.mefuturesaibot.app.ai.learning.*
import com.mefuturesaibot.app.ai.decision.MasterDirection

// Historical outcome statistics only; caller must establish dataset provenance,
// predeclared cohort and strategy version. Not a live signal probability.
internal object UnifiedSignalConfidenceIntegration {
    fun estimate(assessment: UnifiedMasterAssessment, trades: List<TradeExperience>,
        datasetId: String, strategyVersion: String, earliest: Long, latest: Long): ConfidenceEstimate? {
        if (trades.size !in 100..10000 || datasetId.isBlank() || strategyVersion.isBlank() ||
            IndependentConfirmationIntegration.revalidate(assessment,earliest,latest).direction == MasterDirection.WAIT)
            return null
        val stable = trades.toList()
        if (stable.size !in 100..10000 || stable.any { it.closedAtMs >= assessment.evaluatedAtMs }) return null
        return ConfidenceCalibrationEngine.estimate(stable,datasetId,strategyVersion,assessment.evaluatedAtMs)
    }
}
