package com.mefuturesaibot.app.ai

import com.mefuturesaibot.app.exchange.ReconnectingWebSocket
import org.json.JSONObject
import org.json.JSONArray
import android.os.SystemClock
import okhttp3.Request
import java.util.concurrent.TimeUnit

internal class ResearchDepthFeed(private val symbol: String = "BTCUSDT") {
    init { require(symbol in setOf("BTCUSDT","ETHUSDT")) }
    private val book=ResearchDepthBook(symbol)
    private val client=ReconnectingWebSocket.sharedHttpClient.newBuilder()
        .callTimeout(5,TimeUnit.SECONDS).retryOnConnectionFailure(false)
        .followRedirects(false).followSslRedirects(false).build()
    private var snapshotCall: okhttp3.Call? = null
    @Volatile private var closed=false
    @Volatile private var generation=0L
    private val socket=ReconnectingWebSocket(
        url="wss://fstream.binance.com/public/ws/${symbol.lowercase(java.util.Locale.ROOT)}@depth@500ms",
        onStatusChange={ synchronized(this) { generation++; book.clear() } },
        onMessage={ raw -> synchronized(this) {
            if (!closed) try {
                require(raw.length<=131072)
                val o=JSONObject(raw)
                require(o.getString("s")==symbol && o.getString("e")=="depthUpdate")
                book.delta(DepthDelta(o.getLong("U"),o.getLong("u"),o.getLong("pu"),o.getLong("E"),
                    levels(o.getJSONArray("b")),levels(o.getJSONArray("a"))))
            } catch (_: Exception) { generation++; book.clear() }
        } })
    private fun levels(a: JSONArray): List<Pair<java.math.BigDecimal,java.math.BigDecimal>> {
        require(a.length()<=1000)
        return (0 until a.length()).map { i ->
            val row=a.getJSONArray(i)
            require(row.length()==2)
            val p=row.getString(0); val q=row.getString(1)
            require(p.length<=40 && q.length<=40)
            p.toBigDecimal() to q.toBigDecimal()
        }
    }
    fun start()=socket.connect()
    fun refreshSnapshot() {
        val token=synchronized(this) { if (closed||!book.needsSnapshot()) return; generation to book.epoch() }
        val guard=ResearchApiBudget.guard
        if (!guard.acquire(SystemClock.elapsedRealtime(),20)) return
        try {
            val call = synchronized(this) {
                if (closed || generation != token.first || book.epoch() != token.second || snapshotCall != null) return
                client.newCall(Request.Builder().url("https://fapi.binance.com/fapi/v1/depth?symbol=$symbol&limit=500").build())
                    .also { snapshotCall = it }
            }
            try {
                call.execute().use { response ->
                    guard.onHttpResponse(response.code,response.header("Retry-After")?.toLongOrNull(),SystemClock.elapsedRealtime())
                    if (!response.isSuccessful) return
                    val source=response.body.source()
                    if (source.request(262145L)) return
                    val o=JSONObject(source.readUtf8())
                    val bids=levels(o.getJSONArray("bids")); val asks=levels(o.getJSONArray("asks"))
                    synchronized(this) { if (!closed && generation==token.first && book.epoch()==token.second) book.anchor(o.getLong("lastUpdateId"),bids,asks) }
                }
            } finally {
                synchronized(this) { if (snapshotCall === call) snapshotCall = null }
            }
        } catch (_: Exception) { synchronized(this) { if (generation==token.first && book.epoch()==token.second) book.clear() } }
        finally { guard.release() }
    }
    @Synchronized fun liquidity(bounds: AiExchangeClock.Bounds): com.mefuturesaibot.app.ai.decision.LiquiditySnapshot? {
        val value=book.liquidity() ?: return null
        return value.takeIf { !closed && it.observedAtMs<=bounds.earliest && bounds.latest-it.observedAtMs in 0..1999 }
    }
    fun observed()=book.liquidity()!=null
    fun close() {
        synchronized(this) {
            closed = true
            generation++
            snapshotCall?.cancel()
            book.clear()
        }
        socket.close()
    }
}
