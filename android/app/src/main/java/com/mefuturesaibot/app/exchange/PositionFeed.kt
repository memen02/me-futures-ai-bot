package com.mefuturesaibot.app.exchange

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.mefuturesaibot.app.scanner.Quote
import org.json.JSONObject
import java.util.Locale

internal data class PositionMark(val symbol: String, val price: Double, val at: Long, val received: Long) {
    fun fresh(now: Long) = now - received in 0..4000
}

/** Only the held instrument is subscribed. Use server time independently of the event being checked. */
internal class PositionFeed(private val symbol: String, demo: Boolean, private val onMark: (PositionMark?) -> Unit,
    private val onQuote: ((Quote) -> Unit)? = null) {
    private val clockReader = PublicReader(demo)
    private val fallback = PublicReader(demo)
    private val main = Handler(Looper.getMainLooper())
    private var anchor = 0L
    private var anchorElapsed = 0L
    private var nextSync = 0L
    private var nextQuote = 0L
    private var lastQuote = 0L
    private var lastMark = 0L
    private val streams = mutableListOf<ReconnectingWebSocket>()
    private val host = if (demo) "fstream.binancefuture.com" else "fstream.binance.com"

    fun start() {
        val name = PublicReader.encode(symbol.lowercase(Locale.ROOT))
        fun stream(path: String, event: String, apply: (JSONObject) -> Unit) {
            val ws = ReconnectingWebSocket("wss://$host/$path/stream?streams=$name@$event",
                onStatusChange = { if (path == "market" && it != ReconnectingWebSocket.ConnectionStatus.CONNECTED) onMark(null) },
                onMessage = { raw ->
                    val data = runCatching { JSONObject(raw).getJSONObject("data") }.getOrNull()
                    if (data?.optString("s") == symbol) apply(data)
                })
            streams.add(ws)
            ws.connect()
        }
        stream("market","markPrice@1s") { d ->
            val at = d.optLong("E")
            val p = d.optDouble("p")
            if (d.optString("e") == "markPriceUpdate" && valid(at,lastMark) && p.isFinite() && p > 0) {
                lastMark = at
                onMark(PositionMark(symbol,p,at,SystemClock.elapsedRealtime()))
            }
        }
        if (onQuote != null) stream("public","depth5@500ms") { d ->
            if (d.optString("e") == "depthUpdate") {
                val bid = d.optJSONArray("b")?.optJSONArray(0)
                val ask = d.optJSONArray("a")?.optJSONArray(0)
                if (bid != null && ask != null && bid.optDouble(1) > 0 && ask.optDouble(1) > 0)
                    accept(Quote(bid.optDouble(0),ask.optDouble(0),d.optLong("T")))
            }
        }
        tick()
    }
    private fun valid(at: Long, previous: Long): Boolean {
        val elapsed = SystemClock.elapsedRealtime() - anchorElapsed
        return anchor > 0 && elapsed in 0..90_000 && timely(at,previous,anchor + elapsed)
    }
    private fun accept(q: Quote) {
        if (!valid(q.at,lastQuote) || !q.bid.isFinite() || !q.ask.isFinite() || q.bid <= 0 || q.ask < q.bid) return
        lastQuote = q.at
        nextQuote = SystemClock.elapsedRealtime() + 5000
        onQuote?.invoke(q)
    }
    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        if (now >= nextSync) {
            nextSync = now + 10_000
            clockReader.read("time") { body, _, _ ->
                val server = runCatching { JSONObject(body ?: "").getLong("serverTime") }.getOrDefault(0)
                val end = SystemClock.elapsedRealtime()
                if (server > 0 && end-now in 0..2000) {
                    anchor = server
                    anchorElapsed = now + (end-now)/2
                    nextSync = end + 60_000
                }
            }
        }
        if (onQuote != null && now >= nextQuote && anchor > 0) {
            nextQuote = now + 6000
            fallback.read("ticker/bookTicker?symbol=${PublicReader.encode(symbol)}") { body, _, _ ->
                val d = runCatching { JSONObject(body ?: "") }.getOrNull()
                if (d?.optString("symbol") == symbol) accept(Quote(d.optDouble("bidPrice"),d.optDouble("askPrice"),d.optLong("time")))
            }
        }
        main.postDelayed(::tick,1000)
    }
    fun close() {
        main.removeCallbacksAndMessages(null)
        streams.forEach { it.close() }
        streams.clear()
        clockReader.cancel()
        fallback.cancel()
        onMark(null)
    }
    companion object {
        fun timely(at: Long, previous: Long, now: Long) = at > previous && at > 0 && now-at in -1000L..3000L
    }
}
