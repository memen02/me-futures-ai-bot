package com.mefuturesaibot.app.ai

import com.mefuturesaibot.app.ai.decision.*
import java.util.Collections

// Caller must supply order flow bound to the original public REST response.
// No inferred taker volume from OHLCV; missing input remains MISSING.
internal object UnifiedOrderFlowIntegration {
    fun calculate(source: UnifiedTimeframeSnapshot, trend: UnifiedTrendSnapshot,
        flows: Map<Timeframe, OrderFlowSnapshot>, earliest: Long, latest: Long): Map<Timeframe, EvidenceDirection> {
        val structures = UnifiedMarketStructureIntegration.calculate(source,trend,earliest,latest)
        val result = Timeframe.entries.associateWith { frame ->
            val flow = flows[frame]
            val context = trend.frames[frame]
            val structure = structures[frame]
            if (flow == null || context == null || structure == null ||
                flow.symbol != source.symbol || flow.timeframe != frame ||
                flow.closedAtMs != structure.closedAtMs) EvidenceDirection.MISSING
            else flow.direction(context)
        }
        return Collections.unmodifiableMap(result)
    }
}
