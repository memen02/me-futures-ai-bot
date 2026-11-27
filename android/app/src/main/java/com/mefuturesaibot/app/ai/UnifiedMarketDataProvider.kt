package com.mefuturesaibot.app.ai

import java.util.Collections

// Borrowed repository: lifecycle owner remains responsible for closing it.
// Snapshot is validated at capture by repository exchange-clock bounds.
internal class UnifiedMarketDataProvider(private val repository: MultiTimeframeRepository) {
    fun refresh(symbol: String, timeframe: Timeframe): MultiTimeframeRepository.Status {
        require(validSymbol(symbol))
        return repository.refresh(symbol,timeframe)
    }

    fun snapshot(symbol: String): Map<Timeframe, TimeframeHistory> {
        require(validSymbol(symbol))
        val frames = repository.snapshot(symbol)
        if (frames.any { (frame,history) -> history.symbol != symbol || history.timeframe != frame })
            return emptyMap()
        return Collections.unmodifiableMap(frames.mapValues { (_,history) ->
            history.copy(candles = Collections.unmodifiableList(ArrayList(history.candles)))
        })
    }

    companion object {
        fun validSymbol(symbol: String): Boolean = symbol.matches(Regex("[A-Z0-9]{1,32}"))
    }
}
