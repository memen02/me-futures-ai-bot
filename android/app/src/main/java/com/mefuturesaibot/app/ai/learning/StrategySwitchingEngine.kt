package com.mefuturesaibot.app.ai.learning

internal data class StrategyCandidate(val version: String, val stats: ExperienceStats)

internal object StrategySwitchingEngine {
    // Offline shortlist only. Comparable out-of-sample cohorts required.
    fun shortlist(regime: LearningRegime, candidates: List<StrategyCandidate>): String? {
        if (regime == LearningRegime.UNKNOWN || candidates.isEmpty() || candidates.size > 32 ||
            candidates.map { it.version }.toSet().size != candidates.size ||
            candidates.any { it.version.isBlank() || it.stats.count !in 1..10000 ||
                it.stats.wins !in 0..it.stats.count || it.stats.losses !in 0..(it.stats.count-it.stats.wins) ||
                !it.stats.meanNetR.isFinite() || !it.stats.winRate.isFinite() ||
                kotlin.math.abs(it.stats.winRate-it.stats.wins.toDouble()/it.stats.count) > 1e-9 }) return null
        return candidates.filter { it.stats.count >= 100 && it.stats.meanNetR > 0 }
            .sortedWith(compareByDescending<StrategyCandidate> { it.stats.meanNetR }.thenBy { it.version })
            .firstOrNull()?.version
    }
}
