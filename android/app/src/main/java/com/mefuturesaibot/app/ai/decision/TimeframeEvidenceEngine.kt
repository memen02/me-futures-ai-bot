package com.mefuturesaibot.app.ai.decision

import com.mefuturesaibot.app.ai.Timeframe
import com.mefuturesaibot.app.ai.trend.MultiTimeframeTrendContext

internal object TimeframeEvidenceEngine {
    fun direction(context: MultiTimeframeTrendContext, required: Set<Timeframe>, nowMs: Long): EvidenceDirection {
        if (required.size < 2 || context.evaluatedAtMs != nowMs || nowMs <= 0) return EvidenceDirection.MISSING
        val directions = required.map { frame ->
            val value = context.frames[frame] ?: return EvidenceDirection.MISSING
            val snapshot = value.indicators
            if (snapshot.symbol != context.symbol || snapshot.timeframe != frame) return EvidenceDirection.MISSING
            val end = try { Math.addExact(snapshot.closedAtMs,1L) } catch (_: ArithmeticException) { return EvidenceDirection.MISSING }
            val expires = try { frame.nextOpen(end) } catch (_: RuntimeException) { return EvidenceDirection.MISSING }
            if (nowMs < end || nowMs >= expires) return EvidenceDirection.MISSING
            TrendEvidenceEngine.direction(value)
        }
        return when {
            directions.any { it == EvidenceDirection.MISSING } -> EvidenceDirection.MISSING
            directions.all { it == EvidenceDirection.LONG } -> EvidenceDirection.LONG
            directions.all { it == EvidenceDirection.SHORT } -> EvidenceDirection.SHORT
            else -> EvidenceDirection.NEUTRAL
        }
    }
}
