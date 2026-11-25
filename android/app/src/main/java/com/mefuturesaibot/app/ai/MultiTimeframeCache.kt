package com.mefuturesaibot.app.ai

internal class MultiTimeframeCache(
    private val maxSymbols: Int = 8
) {
    init {
        require(maxSymbols in 1..32)
    }

    private val data =
        LinkedHashMap<
            String,
            MutableMap<Timeframe, TimeframeHistory>
        >(16, 0.75f, true)

    @Synchronized
    fun put(history: TimeframeHistory): Boolean {
        require(history.symbol.isNotBlank())

        if (history.candles.isEmpty()) {
            return false
        }

        if (!history.fresh(history.observedAt)) {
            return false
        }

        require(
            history.candles.size <=
                MultiTimeframeHistory.REQUEST_LIMIT
        )

        val existing =
            data[history.symbol]?.get(history.timeframe)

        if (existing != null) {
            if (history.observedAt < existing.observedAt) {
                return false
            }

            if (
                requireNotNull(history.lastOpen) <
                requireNotNull(existing.lastOpen)
            ) {
                return false
            }
        }

        val frames = data.getOrPut(history.symbol) {
            mutableMapOf()
        }

        frames[history.timeframe] = history.copy(
            candles = history.candles.toList()
        )

        while (data.size > maxSymbols) {
            val oldest = data.keys.first()
            data.remove(oldest)
        }

        return true
    }

    @Synchronized
    fun get(
        symbol: String,
        timeframe: Timeframe,
        exchangeTime: Long
    ): TimeframeHistory? {
        val history = data[symbol]?.get(timeframe)
            ?: return null

        return history.takeIf {
            it.fresh(exchangeTime)
        }?.let {
            it.copy(candles = it.candles.toList())
        }
    }

    @Synchronized
    fun availableFrames(
        symbol: String,
        earliest: Long,
        latest: Long
    ): Set<Timeframe> {
        require(earliest <= latest)

        val frames = data[symbol]
            ?: return emptySet()

        return frames.entries
            .filter {
                it.value.fresh(earliest) &&
                    it.value.fresh(latest)
            }
            .map { it.key }
            .toSet()
    }

    @Synchronized
    fun missing(
        symbol: String,
        exchangeTime: Long
    ): List<Timeframe> {
        return Timeframe.entries.filter {
            get(symbol, it, exchangeTime) == null
        }
    }

    @Synchronized
    fun snapshot(
        symbol: String,
        exchangeTime: Long
    ): Map<Timeframe, TimeframeHistory> {
        return Timeframe.entries.mapNotNull { tf ->
            get(symbol, tf, exchangeTime)?.let {
                tf to it
            }
        }.toMap()
    }

    @Synchronized
    fun invalidate(
        symbol: String,
        timeframe: Timeframe
    ) {
        val frames = data[symbol] ?: return
        frames.remove(timeframe)

        if (frames.isEmpty()) {
            data.remove(symbol)
        }
    }

    @Synchronized
    fun symbolCount(): Int = data.size

    @Synchronized
    fun candleCount(): Int =
        data.values.sumOf { frames ->
            frames.values.sumOf { history ->
                history.candles.size
            }
        }

    @Synchronized
    fun clear() {
        data.clear()
    }
}
