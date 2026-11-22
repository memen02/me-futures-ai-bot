package com.mefuturesaibot.app.scanner

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.mefuturesaibot.app.exchange.CandleHistory
import com.mefuturesaibot.app.exchange.ReconnectingWebSocket
import com.mefuturesaibot.app.exchange.SignalSide
import okhttp3.Call
import okhttp3.Request
import org.json.JSONObject
import org.json.JSONArray
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

    private fun funding(symbol: String, from: Long, until: Long): List<Funding> {
        val events = mutableListOf<Funding>()
        var start = from + 1
        while (start <= until) {
            val items = JSONArray(get("fundingRate?symbol=$symbol&startTime=$start&endTime=$until&limit=1000").body)
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val event = Funding(item.getLong("fundingTime"), item.getDouble("fundingRate"), item.getDouble("markPrice"))
                require(event.time in start..until && event.mark > 0 && event.rate.isFinite())
                events.add(event)
            }
            if (items.length() < 1000) break
            start = events.last().time + 1
        }
        return events
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
                var snapshot = Scan(rows, now, SystemClock.elapsedRealtime(), "Scanner aktif", true)
                var confirmation: EntryConfirmation? = null
                var note = when {
                    paper.position != null -> "Memantau posisi virtual"
                    !store.simulation -> "WAIT · simulasi nonaktif"
                    paper.locked(now) -> "WAIT · batas rugi harian"
                    model.rounds < MarketModel.WARMUP -> "WAIT · pemanasan model"
                    else -> "WAIT · belum ada konfirmasi"
                }
                var delay = 60_000L
                if (seekEntry && !paper.locked(now)) {
                    val top = rows.asSequence().filter { it.contract.quote == "USDT" && it.score != null &&
                        abs(it.score - 0.5) > 0.07 && (it.ticker?.volume ?: 0.0) >= 1_000_000 }
                        .maxByOrNull { abs(it.score!! - 0.5) }
                    if (top != null) {
                        try {
                            val path = "klines?symbol=${java.net.URLEncoder.encode(top.contract.symbol, "UTF-8")}&interval=1m&limit=121"
                            val candles = get(path)
                            val history = CandleHistory(CandleHistory.parseClosedCandles(candles.body), "Siap", true)
                            val signal = history.signalAt(candles.serverTime)
                            note = "WAIT · ${top.contract.symbol} · ${signal.reason}"
                            val long = requireNotNull(top.score) > 0.5
                            if (signal.side == (if (long) SignalSide.BUY else SignalSide.SELL)) {
                                candles.serverTime?.let { confirmed ->
                                    confirmation = EntryConfirmation(top.contract.symbol, long, confirmed, Levels.fromClosed(history.candles))
                                    note = "WAIT · menunggu kuotasi setelah konfirmasi"
                                }
                            }
                        } catch (e: Exception) {
                            note = "Scanner aktif · konfirmasi candle tertunda"
                            if (e is Backoff) delay = e.wait
                        }
                    }
                }
                val pending = pendingEntry
                val candidate = pending?.eligible(snapshot)
                val symbol = paper.position?.symbol ?: candidate?.contract?.symbol
                var quote: Quote? = null
                var plan: RiskPlan? = null
                var payments = emptyList<Funding>()
                if (symbol != null) {
                    val book = get("ticker/bookTicker?symbol=$symbol")
                    val json = JSONObject(book.body)
                    require(json.getString("symbol") == symbol)
                    val at = book.serverTime ?: throw IOException("Waktu bursa kosong")
                    quote = Quote(json.getDouble("bidPrice"), json.getDouble("askPrice"), json.getLong("time"))
                    snapshot = snapshot.copy(time = at)
                    val held = paper.position
                    if (held != null) {
                        payments = funding(symbol, held.fundingAt, at)
                    } else if (candidate != null && pending?.levels != null) {
                        val nextFunding = JSONObject(get("premiumIndex?symbol=$symbol").body).getLong("nextFundingTime")
                        try {
                            require(nextFunding > at + 31 * 60_000L) { "mendekati settlement funding" }
                            plan = RiskPlan.build(if (pending.long) 1 else -1, quote, pending.levels, paper.balance, at)
                        } catch (e: IllegalArgumentException) {
                            note = "WAIT · $symbol · ${e.message}"
                        }
                    }
                }
                handler.post {
                    if (!closed) {
                        val timely = SystemClock.elapsedRealtime() - snapshot.received in 0L..5000L
                        val account = if (timely) paper.advance(snapshot, symbol, plan, store.simulation, quote, payments) else paper
                        val outcome = when {
                            !timely -> "WAIT · pemindaian kedaluwarsa"
                            paper.position == null && account.position != null -> "Virtual · ${account.journal.first().substringAfter('|')}"
                            paper.position != null && account.position == null -> account.journal.first().substringAfter('|')
                            else -> note
                        }
                        val next = snapshot.copy(status = outcome)
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
                        previous = previous.copy(ok = false, status = if (e is Backoff) "WAIT · ${e.message}" else "WAIT · periksa koneksi dan data bursa")
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
