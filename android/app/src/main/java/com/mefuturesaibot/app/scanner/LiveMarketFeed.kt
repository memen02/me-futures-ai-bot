package com.mefuturesaibot.app.scanner

import android.os.SystemClock
import com.mefuturesaibot.app.exchange.ReconnectingWebSocket
import org.json.JSONArray
import org.json.JSONObject

internal class LiveMarketFeed {

    private data class Sample(
        val ticker: Ticker,
        val received: Long
    )

    private val cache = mutableMapOf<String, Sample>()
    private var connected = false
    private var connectionState = "CONNECTING"
    private var lastReceived = -1L

    fun status(): String {
        if (!connected) return connectionState
        if (lastReceived < 0L) return "WAITING DATA"

        val age = SystemClock.elapsedRealtime() - lastReceived

        return if (age in 0L..MAX_AGE_MS)
            "LIVE" else "STALE"
    }

    private val socket = ReconnectingWebSocket(
        url = STREAM_URL,
        onStatusChange = { status ->
            connected = status ==
                ReconnectingWebSocket.ConnectionStatus.CONNECTED

            connectionState = status.name

            if (!connected) {
                cache.clear()
                lastReceived = -1L
            }
        },
        onMessage = { message ->
            accept(message)
        }
    )

    fun start() {
        socket.connect()
    }

    private fun accept(message: String) {
        val updates = parse(message)
        val now = SystemClock.elapsedRealtime()

        for ((symbol, ticker) in updates) {
            val previous = cache[symbol]

            if (previous == null ||
                ticker.time > previous.ticker.time
            ) {
                if (previous != null ||
                    cache.size < MAX_SYMBOLS
                ) {
                    cache[symbol] = Sample(ticker, now)
                    lastReceived = now
                }
            }
        }
    }

    fun ticker(symbol: String): Ticker? {
        if (!connected) return null

        val sample = cache[symbol] ?: return null
        val age = SystemClock.elapsedRealtime() - sample.received

        return sample.ticker.takeIf {
            age in 0L..MAX_AGE_MS
        }
    }

    fun close() {
        connected = false
        socket.close()
        cache.clear()
    }

    companion object {
        const val STREAM_URL =
            "wss://fstream.binance.com/market/ws/!ticker@arr"

        private const val MAX_SYMBOLS = 4096
        private const val MAX_AGE_MS = 15_000L

        internal fun parse(
            message: String
        ): Map<String, Ticker> {
            val array = try {
                if (message.trimStart().startsWith("[")) {
                    JSONArray(message)
                } else {
                    JSONObject(message).getJSONArray("data")
                }
            } catch (_: Exception) {
                return emptyMap()
            }

            val result = mutableMapOf<String, Ticker>()

            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue

                if (item.optString("e") != "24hrTicker")
                    continue

                val symbol = item.optString("s")
                if (symbol.isBlank()) continue

                val ticker = Ticker(
                    price = item.optDouble("c"),
                    change = item.optDouble("P"),
                    high = item.optDouble("h"),
                    low = item.optDouble("l"),
                    volume = item.optDouble("q"),
                    average = item.optDouble("w"),
                    time = item.optLong("E")
                )

                if (
                    ticker.price.isFinite() &&
                    ticker.price > 0 &&
                    ticker.change.isFinite() &&
                    ticker.high.isFinite() &&
                    ticker.low.isFinite() &&
                    ticker.low > 0 &&
                    ticker.price in ticker.low..ticker.high &&
                    ticker.volume.isFinite() &&
                    ticker.volume >= 0 &&
                    ticker.average.isFinite() &&
                    ticker.average > 0 &&
                    ticker.time > 0
                ) {
                    val previous = result[symbol]
                    if (previous == null ||
                        ticker.time >= previous.time
                    ) {
                        result[symbol] = ticker
                    }
                }
            }

            return result
        }
    }
}
