package com.mefuturesaibot.app.ai

import com.mefuturesaibot.app.ai.decision.*

// Trusted caller must establish synchronized depth and symmetric price bands.
// Check both exchange-clock bounds, not just the nominal observation time.
internal object UnifiedLiquidityIntegration {
    fun direction(symbol: String, book: LiquiditySnapshot?, earliest: Long, latest: Long): EvidenceDirection {
        if (!UnifiedMarketDataProvider.validSymbol(symbol) || earliest <= 0 ||
            latest < earliest || book == null) return EvidenceDirection.MISSING
        val first = book.direction(symbol,earliest)
        val last = book.direction(symbol,latest)
        return if (first == last) first else EvidenceDirection.MISSING
    }
}
