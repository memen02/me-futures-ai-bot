package com.mefuturesaibot.app.ai.learning

// Bounded in-memory analytical cache, not the authoritative trade ledger.
internal class MarketMemory(private val datasetId: String, private val capacity: Int = 2000) {
    init { require(datasetId.isNotBlank() && capacity in 1..10000) }
    private val records = LinkedHashMap<String, TradeExperience>()

    @Synchronized fun record(trade: TradeExperience, nowMs: Long): Boolean {
        if (!trade.valid() || trade.datasetId != datasetId || trade.closedAtMs > nowMs) return false
        val existing = records[trade.tradeId]
        if (existing != null) return existing == trade
        // Do not silently evict observations or change the sample population.
        if (records.size >= capacity) return false
        records[trade.tradeId] = trade
        return true
    }

    @Synchronized fun snapshot(strategyVersion: String, asOfMs: Long): List<TradeExperience> =
        records.values.filter { it.strategyVersion == strategyVersion && it.closedAtMs <= asOfMs }
            .sortedWith(compareBy<TradeExperience> { it.closedAtMs }.thenBy { it.tradeId })
}
