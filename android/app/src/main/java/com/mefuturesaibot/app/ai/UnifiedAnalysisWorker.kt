package com.mefuturesaibot.app.ai

import com.mefuturesaibot.app.ai.decision.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

// Borrowed input snapshots only. No network polling or service start.
// The lifecycle owner must close this worker and supply trusted clock bounds.
internal class UnifiedAnalysisWorker(private val clock: () -> AiExchangeClock.Bounds?) : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private val publisher = UnifiedSignalPublisher()
    @Volatile private var closed = false

    @Synchronized fun submit(source: UnifiedTimeframeSnapshot, primary: Timeframe,
        required: Set<Timeframe>, flows: Map<Timeframe,OrderFlowSnapshot>, book: LiquiditySnapshot?): Boolean {
        if (closed || !busy.compareAndSet(false,true)) return false
        publisher.clear()
        val frames = required.toSet()
        val stable = flows.toMap()
        return try {
            executor.execute {
                try {
                    val bounds = clock()
                    if (!closed && bounds != null) publisher.publish(source,primary,frames,stable,book,bounds)
                } catch (_: Exception) { publisher.clear() }
                finally { busy.set(false) }
            }
            true
        } catch (_: RuntimeException) { busy.set(false); false }
    }

    fun read(symbol: String): MasterDecision {
        val wait = MasterDecision(MasterDirection.WAIT,DecisionReason.INVALID_EVIDENCE,0)
        if (closed || busy.get()) return wait
        val bounds = clock() ?: return wait
        return publisher.read(symbol,bounds)
    }

    @Synchronized override fun close() {
        closed = true
        executor.shutdownNow()
        publisher.close()
    }
}
