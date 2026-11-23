package com.mefuturesaibot.app

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.mefuturesaibot.app.exchange.PublicReader
import com.mefuturesaibot.app.exchange.ReconnectingWebSocket
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.util.TreeMap
import java.util.Locale

/** Synchronize USDⓈ-M diff depth using the exchange update IDs, retaining the snapshot's covered prices. */
internal class DepthBook(snapshot: JSONObject) {
    val bids = TreeMap<BigDecimal, BigDecimal>(reverseOrder())
    val asks = TreeMap<BigDecimal, BigDecimal>()
    private var last = snapshot.getLong("lastUpdateId")
    private var bridged = false
    private val floor: BigDecimal
    private val ceiling: BigDecimal
    var time = snapshot.getLong("T")
        private set

    init {
        levels(snapshot.getJSONArray("bids"), bids) { true }
        levels(snapshot.getJSONArray("asks"), asks) { true }
        floor = bids.lastKey()
        ceiling = asks.lastKey()
    }

    private fun levels(rows: JSONArray, side: MutableMap<BigDecimal, BigDecimal>, covered: (BigDecimal) -> Boolean) {
        for (i in 0 until rows.length()) {
            val row = rows.getJSONArray(i)
            val p = row.getString(0).toBigDecimal()
            val q = row.getString(1).toBigDecimal()
            require(p.signum() > 0 && q.signum() >= 0)
            if (covered(p)) {
                if (q.signum() == 0) side.remove(p) else side[p] = q
            }
        }
    }

    fun apply(event: JSONObject): Boolean {
        val end = event.getLong("u")
        if (end < last || bridged && end == last) return false
        require(if (bridged) event.getLong("pu") == last else event.getLong("U") <= last)
        levels(event.getJSONArray("b"), bids) { it >= floor }
        levels(event.getJSONArray("a"), asks) { it <= ceiling }
        require(bids.size >= 5 && asks.size >= 5 && bids.firstKey() < asks.firstKey())
        last = end
        time = event.getLong("T")
        bridged = true
        return true
    }
}

internal class DashboardDepth(private val symbol: String, private val publish: (DepthBook?, String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val reader = PublicReader()
    private var socket: WebSocket? = null
    private var book: DepthBook? = null
    private val buffer = ArrayDeque<JSONObject>()
    private var lastMessage = 0L
    private var backoff = 2000L
    private val watchdog = object : Runnable {
        override fun run() {
            if (SystemClock.elapsedRealtime() - lastMessage > 15_000) restart("Data kedaluwarsa")
            else main.postDelayed(this, 5000)
        }
    }

    fun start() {
        publish(null, "Menyinkronkan buku order…")
        lastMessage = SystemClock.elapsedRealtime()
        val stream = PublicReader.encode(symbol.lowercase(Locale.ROOT))
        val request = Request.Builder().url("wss://fstream.binance.com/public/stream?streams=$stream@depth@500ms").build()
        socket = ReconnectingWebSocket.sharedHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                main.post {
                    if (socket !== webSocket) return@post
                    reader.read("depth?symbol=${PublicReader.encode(symbol)}&limit=100") { body, _, error ->
                        try {
                            val next = DepthBook(JSONObject(requireNotNull(body)))
                            book = next
                            while (buffer.isNotEmpty()) apply(next, buffer.removeFirst())
                        } catch (_: Exception) { restart(error.ifEmpty { "Snapshot tidak valid" }) }
                    }
                }
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                main.post {
                    if (socket !== webSocket) return@post
                    try {
                        val event = JSONObject(text).getJSONObject("data")
                        require(event.getString("s") == symbol && event.getString("e") == "depthUpdate")
                        val current = book
                        if (current == null) { require(buffer.size < 128); buffer.addLast(event) }
                        else apply(current, event)
                    } catch (_: Exception) { restart("Urutan pembaruan tidak valid") }
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = disconnected(webSocket)
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = disconnected(webSocket)
        })
        main.postDelayed(watchdog, 5000)
    }

    private fun apply(current: DepthBook, event: JSONObject) {
        if (current.apply(event)) {
            lastMessage = SystemClock.elapsedRealtime()
            backoff = 2000
            publish(current, "Tersinkron")
        }
    }
    private fun disconnected(source: WebSocket) {
        main.post { if (source === socket) restart("Koneksi terputus") }
    }
    private fun restart(reason: String) {
        stop()
        publish(null, "$reason · menyambung ulang")
        main.postDelayed(::start, backoff)
        backoff = minOf(60_000, backoff * 2)
    }
    fun stop() {
        main.removeCallbacksAndMessages(null)
        reader.cancel()
        val old = socket
        socket = null
        old?.cancel()
        buffer.clear()
        book = null
    }
}
