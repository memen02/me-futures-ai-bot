package com.mefuturesaibot.app.ai

import com.mefuturesaibot.app.ai.decision.EvidenceDirection
import com.mefuturesaibot.app.ai.decision.MasterDirection
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal interface ResearchPilotSession : AutoCloseable {
    fun status(): String
}

/** The service calls status and close on the main looper. Only an active bot owns a session. */
internal class ResearchPilotLifecycle(
    private val elapsed: () -> Long = android.os.SystemClock::elapsedRealtime,
    private val open: () -> ResearchPilotSession
) : AutoCloseable {
    private var session: ResearchPilotSession? = null
    private var destroyed = false
    private var retryAt = 0L

    fun status(botActive: Boolean): String {
        if (!botActive || destroyed) {
            session?.close()
            session = null
            retryAt = 0L
            return "OFFLINE"
        }
        session?.let { return it.status() }
        if (elapsed() < retryAt) return "WAIT · koneksi riset tertunda"
        return try {
            open().also { session = it }.status()
        } catch (_: RuntimeException) {
            session?.close()
            session = null
            retryAt = elapsed() + 30_000L
            "WAIT · koneksi riset tertunda"
        }
    }

    override fun close() {
        destroyed = true
        session?.close()
        session = null
    }
}

/** All publications, depth epochs and candle caches belong to this activation only. */
internal class MarketResearchPilot : ResearchPilotSession {
    private val symbols = listOf("BTCUSDT", "ETHUSDT")
    private val frames = setOf(Timeframe.M1, Timeframe.M5)
    private val repository = MultiTimeframeRepository(guard = ResearchApiBudget.guard)
    private val feeds = symbols.associateWith { ResearchDepthFeed(it) }
    private val workers = symbols.associateWith { UnifiedAnalysisWorker { repository.clockBounds() } }
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var cursor = 0
    @Volatile private var closed = false

    init {
        try {
            feeds.values.forEach { it.start() }
            scheduler.scheduleWithFixedDelay(::refresh, 2, 2, TimeUnit.SECONDS)
        } catch (error: RuntimeException) {
            close()
            throw error
        }
    }

    private fun refresh() {
        if (closed) return
        try {
            val slot = cursor
            cursor = (cursor + 1) % (3 * symbols.size)
            val symbol = symbols[slot % symbols.size]
            when (slot / symbols.size) {
                0 -> repository.refresh(symbol, Timeframe.M1)
                1 -> repository.refresh(symbol, Timeframe.M5)
                else -> feeds.getValue(symbol).refreshSnapshot()
            }
            for ((pair, feed) in feeds) {
                if (closed) return
                val (source, flows) = repository.researchCapture(pair) ?: continue
                workers.getValue(pair).submit(source, Timeframe.M1, frames, flows, repository.clockBounds()?.let(feed::liquidity))
            }
        } catch (_: Exception) {
            // The next tick revalidates clock, depth and candle freshness before display.
        }
    }

    override fun status(): String {
        val bounds = repository.clockBounds()
        return symbols.joinToString("\n") { symbol ->
            val book = bounds?.let(feeds.getValue(symbol)::liquidity)
            if (bounds == null || book == null) return@joinToString "$symbol · WAIT · depth tidak valid"
            val decision = workers.getValue(symbol).read(symbol)
            val expected = when (decision.direction) {
                MasterDirection.LONG -> EvidenceDirection.LONG
                MasterDirection.SHORT -> EvidenceDirection.SHORT
                else -> null
            }
            if (expected != null && book.direction(symbol, bounds.latest) != expected)
                "$symbol · WAIT · likuiditas berubah"
            else "$symbol · ${decision.direction} · ${decision.reason} · ${decision.confirmations}"
        }
    }

    override fun close() {
        closed = true
        scheduler.shutdownNow()
        feeds.values.forEach { it.close() }
        repository.close()
        workers.values.forEach { it.close() }
    }
}
