package com.mefuturesaibot.app.ai

import com.mefuturesaibot.app.ai.decision.*

// One bounded research slot. Never an order authorization.
internal class UnifiedSignalPublisher : AutoCloseable {
    private data class Slot(val source: UnifiedTimeframeSnapshot, val assessment: UnifiedMasterAssessment)
    private var slot: Slot? = null
    private var closed = false

    @Synchronized fun publish(source: UnifiedTimeframeSnapshot, primary: Timeframe,
        required: Set<Timeframe>, flows: Map<Timeframe,OrderFlowSnapshot>, book: LiquiditySnapshot?,
        bounds: AiExchangeClock.Bounds): Boolean {
        if (closed) return false
        slot = null
        if (!source.fresh(bounds.earliest,bounds.latest)) return false
        val result = UnifiedMasterAiIntegration.evaluate(source,primary,required,flows,book,
            bounds.earliest,bounds.latest)
        slot = Slot(source,result)
        return true
    }

    @Synchronized fun read(symbol: String, bounds: AiExchangeClock.Bounds): MasterDecision {
        val wait = MasterDecision(MasterDirection.WAIT,DecisionReason.INVALID_EVIDENCE,0)
        val current = slot ?: return wait
        if (closed || symbol != current.source.symbol ||
            !current.source.fresh(bounds.earliest,bounds.latest)) return wait
        return IndependentConfirmationIntegration.revalidate(current.assessment,bounds.earliest,bounds.latest)
    }

    @Synchronized fun clear() { slot = null }
    @Synchronized override fun close() { closed = true; slot = null }
}
