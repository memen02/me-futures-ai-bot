package com.mefuturesaibot.app.ai.decision

import com.mefuturesaibot.app.ai.indicators.IndicatorSnapshot

internal object MomentumEvidenceEngine {
    fun direction(snapshot: IndicatorSnapshot): EvidenceDirection {
        val rsi = snapshot.rsi14 ?: return EvidenceDirection.MISSING
        val histogram = snapshot.macd?.histogram ?: return EvidenceDirection.MISSING
        val roc = snapshot.roc12 ?: return EvidenceDirection.MISSING
        if (!rsi.isFinite() || rsi !in 0.0..100.0 || !histogram.isFinite() || !roc.isFinite())
            return EvidenceDirection.MISSING
        return when {
            rsi in 55.0..70.0 && histogram > 0 && roc > 0 -> EvidenceDirection.LONG
            rsi in 30.0..45.0 && histogram < 0 && roc < 0 -> EvidenceDirection.SHORT
            else -> EvidenceDirection.NEUTRAL
        }
    }
}
