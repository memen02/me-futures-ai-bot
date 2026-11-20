package com.mefuturesaibot.app.scanner

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.mefuturesaibot.app.exchange.CandleHistory
import com.mefuturesaibot.app.exchange.ReconnectingWebSocket
import com.mefuturesaibot.app.exchange.SignalSide
import okhttp3.Call
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.math.abs

/** A single worker owns the registry and performs one sequential scan at a time. */
internal class MarketScanner(private val store: DeskStore, private val publish: (Scan) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var registry = emptyList<Contract>()
    private var registryAt = 0L
    private var previous = Scan()
    private var started = false
    private var pendingEntry: EntryConfirmation? = null
    @Volatile private var closed = false
    @Volatile private var call: Call? = null

    private class Backoff(code: Int, val wait: Long) : IOException("Binance HTTP $code")
    private data class Reply(val body: String, val serverTime: Long?)
    private fun get(path: String): Reply {
        val request = Request.Builder().url("https://fapi.binance.com/fapi/v1/$path").build()
        val current = ReconnectingWebSocket.sharedHttpClient.newCall(request)
        call = current
        if (closed) { current.cancel(); throw IOException("Dihentikan") }
        return current.execute().use {
            if (!it.isSuccessful) {
                val seconds = it.header("Retry-After")?.toLongOrNull()?.coerceIn(0L, Int.MAX_VALUE.toLong()) ?: 60L
                throw Backoff(it.code, maxOf(60_000L, seconds * 1000L))
            }
            Reply(it.body.string(), it.headers.getDate("Date")?.time)
        }
    }

    fun start() {
        if (started || closed) return
        started = true
        publish(Scan(status = "Memuat kontrak dan ticker Binance"))
        cycle()
    }

    private fun cycle() {
        if (closed) return
        val (baseModel, paper) = store.read()
        val seekEntry = store.simulation && paper.position == null
        worker.execute {
            try {
                val elapsed = SystemClock.elapsedRealtime()
                if (registry.isEmpty() || elapsed - registryAt >= 3_600_000L) {
                    registry = MarketData.contracts(get("exchangeInfo").body)
                    registryAt = elapsed
                }
                val reply = get("ticker/24hr")
                val tickers = MarketData.tickers(reply.body)
                val now = reply.serverTime ?: throw IOException("Waktu respons bursa tidak tersedia")
                val model = baseModel.learn(previous, tickers, now)
                val rows = registry.map { contract ->
                    val t = tickers[contract.symbol]
                    MarketRow(contract, t, t?.takeIf { it.fresh(now) }?.let(model::score))
                }
                val snapshot = Scan(rows, now, SystemClock.elapsedRealtime(), "Scanner aktif", true)
                var confirmation: EntryConfirmation? = null
                var note = snapshot.status
                var delay = 60_000L
                if (seekEntry) {
                    val top = rows.asSequence().filter { it.contract.quote == "USDT" && it.score != null &&
                        abs(it.score - 0.5) > 0.07 && (it.ticker?.volume ?: 0.0) >= 1_000_000 }
                        .maxByOrNull { abs(it.score!! - 0.5) }
                    if (top != null) {
                        try {
                            val path = "klines?symbol=${java.net.URLEncoder.encode(top.contract.symbol, "UTF-8")}&interval=1m&limit=121"
                            val candles = get(path)
                            val history = CandleHistory(CandleHistory.parseClosedCandles(candles.body), "Siap", true)
                            val signal = history.signalAt(candles.serverTime).side
                            val long = requireNotNull(top.score) > 0.5
                            if (signal == (if (long) SignalSide.BUY else SignalSide.SELL)) {
                                candles.serverTime?.let { confirmed ->
                                    confirmation = EntryConfirmation(top.contract.symbol, long, confirmed)
                                }
                            }
                        } catch (e: Exception) {
                            note = "Scanner aktif · konfirmasi candle tertunda"
                            if (e is Backoff) delay = e.wait
                        }
                    }
                }
                handler.post {
                    if (!closed) {
                        val next = snapshot.copy(status = note)
                        val timely = next.fresh(SystemClock.elapsedRealtime())
                        val candidate = pendingEntry?.eligible(next)
                        val account = if (timely) paper.advance(next, candidate, store.simulation) else paper
                        pendingEntry = if (timely && store.simulation && account.position == null) confirmation else null
                        store.save(model, account)
                        previous = next
                        publish(next)
                        handler.postDelayed({ cycle() }, delay)
                    }
                }
            } catch (e: Exception) {
                handler.post {
                    if (!closed) {
                        pendingEntry = null
                        previous = previous.copy(ok = false, status = if (e is Backoff) e.message.orEmpty() else "Scanner gagal · cek jaringan atau data bursa")
                        publish(previous)
                        handler.postDelayed({ cycle() }, (e as? Backoff)?.wait ?: 60_000L)
                    }
                }
            }
        }
    }

    fun close() {
        closed = true
        handler.removeCallbacksAndMessages(null)
        call?.cancel()
        worker.shutdownNow()
    }
}
