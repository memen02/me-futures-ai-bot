package com.mefuturesaibot.app.exchange

import java.io.IOException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException

/** Start, refresh, stop, dan publikasi hasil memakai scheduler thread utama. */
class CandleHistorySync(
    private val client: Call.Factory = ReconnectingWebSocket.sharedHttpClient,
    private val scheduler: Scheduler = AndroidHandlerScheduler(),
    private val nowMs: () -> Long = android.os.SystemClock::elapsedRealtime,
    private val clock: ExchangeClock = ExchangeClock(),
    private val onUpdate: (CandleHistory) -> Unit,
) {
    private val request = Request.Builder()
        .url("https://fapi.binance.com/fapi/v1/klines?symbol=BTCUSDT&interval=1m&limit=${CandleHistory.SIZE + 1}")
        .build()
    private var active = false
    private var current: Call? = null
    private var history = CandleHistory()
    private var retryAfterMs = 0L

    fun start() {
        if (active) return
        active = true
        refresh()
    }

    fun refresh() {
        if (!active || current != null) return
        scheduler.cancelAll()
        val remaining = retryAfterMs - nowMs()
        if (remaining > 0) {
            scheduler.postDelayed(remaining) { refresh() }
            return
        }
        val call = client.newCall(request)
        current = call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                complete(call, error = "Riwayat gagal dimuat")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        val delay = it.header("Retry-After")?.toLongOrNull()?.coerceIn(0, Int.MAX_VALUE.toLong())
                        val pause = if (it.code == 429 || it.code == 418) {
                            maxOf(CandleHistory.INTERVAL_MS, (delay ?: 60L) * 1000L)
                        } else 0L
                        complete(call, error = "Riwayat gagal dimuat (HTTP ${it.code})", pause = pause)
                        return
                    }
                    try {
                        val received = nowMs()
                        val date = it.headers.getDate("Date")?.time
                        val candles = CandleHistory.parseClosedCandles(it.body.string())
                        complete(call, candles, serverDateMs = date, receivedMs = received)
                    } catch (_: IOException) {
                        complete(call, error = "Unduhan riwayat terputus")
                    } catch (_: JSONException) {
                        complete(call, error = "Format riwayat tidak valid")
                    } catch (_: IllegalArgumentException) {
                        complete(call, error = "Riwayat tidak lengkap atau tidak valid")
                    }
                }
            }
        })
    }

    private fun complete(
        call: Call,
        candles: List<Candle>? = null,
        error: String = "",
        pause: Long = 0L,
        serverDateMs: Long? = null,
        receivedMs: Long = 0L,
    ) {
        scheduler.post {
            if (current !== call) return@post
            current = null
            if (serverDateMs != null) clock.observe(serverDateMs, receivedMs)
            retryAfterMs = nowMs() + pause
            val lastTime = history.candles.lastOrNull()?.openTime ?: -1L
            history = when {
                candles == null -> history.copy(status = error, syncOk = false)
                candles.last().openTime < lastTime -> history.copy(status = "Menunggu riwayat terbaru", syncOk = false)
                else -> CandleHistory(candles, "Riwayat tersinkron", syncOk = true)
            }
            onUpdate(history)
            // Ambil sesaat setelah batas menit berikut, ketika candle baru telah ditutup.
            val untilUpdate = clock.at(nowMs())?.let { exchangeMs ->
                CandleHistory.INTERVAL_MS - Math.floorMod(exchangeMs - 1500L, CandleHistory.INTERVAL_MS)
            } ?: CandleHistory.INTERVAL_MS
            val delayMs = if (history.syncOk) untilUpdate else maxOf(CandleHistory.INTERVAL_MS, pause)
            scheduler.postDelayed(delayMs) { refresh() }
        }
    }

    fun stop() {
        active = false
        val pending = current
        current = null
        scheduler.cancelAll()
        pending?.cancel()
        history = CandleHistory()
        onUpdate(history)
    }
}
