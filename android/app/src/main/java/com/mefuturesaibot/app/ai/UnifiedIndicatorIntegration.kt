package com.mefuturesaibot.app.ai

import com.mefuturesaibot.app.ai.indicators.MultiTimeframeIndicators
import com.mefuturesaibot.app.ai.indicators.MultiTimeframeIndicatorEngine

// Worker-only calculation; no trading authorization.
internal object UnifiedIndicatorIntegration {
    fun calculate(snapshot: UnifiedTimeframeSnapshot): MultiTimeframeIndicators? {
        if (!snapshot.fresh(snapshot.earliest,snapshot.latest)) return null
        return MultiTimeframeIndicatorEngine.calculate(snapshot.symbol,snapshot.frames,snapshot.latest)
    }
}
