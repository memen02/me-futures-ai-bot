package com.mefuturesaibot.app.ai

import com.mefuturesaibot.app.exchange.Candle
import org.json.JSONArray

internal data class TimeframeHistory(
    val symbol: String,
    val timeframe: Timeframe,
    val candles: List<Candle>,
    val observedAt: Long
) {
    val lastOpen: Long?
        get() = candles.lastOrNull()?.openTime

    fun fresh(exchangeTime: Long): Boolean {
        val last = lastOpen ?: return false

        if (exchangeTime < observedAt) return false

        val next = timeframe.nextOpen(last)
        val expires = timeframe.nextOpen(next)

        return exchangeTime >= next &&
            exchangeTime < expires
    }

    fun ready(
        minimum: Int,
        exchangeTime: Long
    ): Boolean {
        require(minimum > 0)

        return candles.size >= minimum &&
            fresh(exchangeTime)
    }
}

internal object MultiTimeframeHistory {

    const val REQUEST_LIMIT = 499

    fun parse(
        symbol: String,
        timeframe: Timeframe,
        body: String,
        exchangeTime: Long
    ): TimeframeHistory {

        require(symbol.isNotBlank())
        require(exchangeTime > 0)

        val rows = JSONArray(body)

        require(rows.length() in 1..REQUEST_LIMIT)

        val closed = ArrayList<Candle>(rows.length())

        var previousOpen: Long? = null

        for (i in 0 until rows.length()) {
            val row = rows.getJSONArray(i)

            val openTime = row.getLong(0)
            val closeTime = row.getLong(6)

            require(timeframe.validOpen(openTime)) {
                "Waktu pembukaan candle tidak valid"
            }

            val nextOpen = timeframe.nextOpen(openTime)

            require(closeTime == nextOpen - 1L) {
                "Waktu penutupan candle tidak valid"
            }

            require(openTime <= exchangeTime) {
                "Candle berasal dari masa depan"
            }

            previousOpen?.let { previous ->
                require(
                    openTime == timeframe.nextOpen(previous)
                ) {
                    "Candle terputus, duplikat atau tidak berurutan"
                }
            }

            previousOpen = openTime

            val candle = Candle.fromKline(row)

            if (nextOpen <= exchangeTime) {
                closed.add(candle)
            }
        }

        return TimeframeHistory(
            symbol = symbol,
            timeframe = timeframe,
            candles = closed.toList(),
            observedAt = exchangeTime
        )
    }
}
