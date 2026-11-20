package com.mefuturesaibot.app.exchange

import org.json.JSONException
import org.json.JSONObject

data class BookQuote(
    val bid: String,
    val ask: String,
    val eventTimeMs: Long,
    val receivedElapsedMs: Long,
    val disconnected: Boolean = false,
) {
    fun isStale(nowElapsedMs: Long, nowEpochMs: Long): Boolean =
        disconnected || nowElapsedMs - receivedElapsedMs >= MAX_AGE_MS ||
            nowEpochMs - eventTimeMs >= MAX_AGE_MS

    companion object {
        private const val MAX_AGE_MS = 10_000L

        fun parse(message: String, receivedElapsedMs: Long): BookQuote? {
            val envelope = try {
                JSONObject(message)
            } catch (_: JSONException) {
                return null
            }
            val data = envelope.optJSONObject("data") ?: envelope
            if (data.optString("e") != "bookTicker" || data.optString("s") != "BTCUSDT") return null
            val bid = data.optString("b")
            val ask = data.optString("a")
            val bidValue = bid.toBigDecimalOrNull() ?: return null
            val askValue = ask.toBigDecimalOrNull() ?: return null
            val eventTime = data.optLong("E", 0L)
            if (bidValue.signum() <= 0 || askValue < bidValue || eventTime <= 0L) return null
            return BookQuote(bid, ask, eventTime, receivedElapsedMs)
        }
    }
}
