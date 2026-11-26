package com.mefuturesaibot.app.ai.risk

internal object TradePlanFreshness {
    // All timestamps must share the same trusted exchange clock domain.
    fun valid(createdAtMs: Long, quoteAtMs: Long, signalExpiresAtMs: Long,
              nowMs: Long): Boolean {
        return quoteAtMs > 0 && createdAtMs >= quoteAtMs && nowMs >= createdAtMs &&
            nowMs < signalExpiresAtMs && nowMs-quoteAtMs < 2000L &&
            nowMs-createdAtMs < 1000L
    }
}
