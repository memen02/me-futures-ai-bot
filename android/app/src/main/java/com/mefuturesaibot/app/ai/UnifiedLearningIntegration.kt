package com.mefuturesaibot.app.ai

import com.mefuturesaibot.app.ai.learning.*

// Offline evaluation only. No strategy promotion or trading side effects.
internal object UnifiedLearningIntegration {
    fun evaluate(trades: List<TradeExperience>, datasetId: String, version: String,
        asOfMs: Long): LearningReport? {
        if (trades.size !in 1..10000 || datasetId.isBlank() || version.isBlank() || asOfMs <= 0) return null
        val stable = trades.toList()
        if (stable.size !in 1..10000 || stable.any { it.closedAtMs >= asOfMs }) return null
        if (ExperienceEvaluator.evaluate(stable,datasetId,version,asOfMs) == null) return null
        val memory = MarketMemory(datasetId,10000)
        if (stable.any { !memory.record(it,asOfMs) }) return null
        return LearningCoordinator.evaluate(memory,datasetId,version,asOfMs)
    }
}
