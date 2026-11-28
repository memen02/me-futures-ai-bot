package com.mefuturesaibot.app.ai

import com.mefuturesaibot.app.exchange.ReconnectingWebSocket
import org.json.JSONObject

// Public observation only. Partial depth is not accepted as synchronized liquidity
// until snapshot semantics and coverage have been verified against the venue.
internal class ResearchDepthFeed {
    @Volatile private var event: String? = null
    private val socket = ReconnectingWebSocket(
        url="wss://fstream.binance.com/public/ws/btcusdt@depth20@500ms",
        onStatusChange={ event = null },
        onMessage={ raw ->
            event = if (raw.length <= 16384 && valid(raw)) raw else null
        })
    private fun valid(raw: String): Boolean = try {
        val obj = JSONObject(raw)
        obj.getString("s") == "BTCUSDT" && obj.getString("e") == "depthUpdate" &&
            obj.getLong("E") > 0 && obj.getJSONArray("b").length() in 1..20 &&
            obj.getJSONArray("a").length() in 1..20
    } catch (_: Exception) { false }
    fun start() = socket.connect()
    fun observed(): Boolean = event != null
    fun close() { socket.close(); event = null }
}
