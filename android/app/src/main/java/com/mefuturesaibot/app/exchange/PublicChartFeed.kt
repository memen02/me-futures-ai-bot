package com.mefuturesaibot.app.exchange

import java.io.IOException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray

internal val chartIntervals = listOf("1s", "1m", "3m", "5m", "15m", "30m", "1h", "2h", "4h",
    "6h", "8h", "12h", "1d", "3d", "1w", "1M")

internal data class PublicChart(
    val interval: String,
    val bars: List<Candle> = emptyList(),
    val status: String = "Memuat candle…",
    val fresh: Boolean = false,
    val updatedAt: Long? = null,
)

/** Maintain only the selected public market window; lifecycle and interval changes run on the UI thread. */
internal class PublicChartFeed(
    private val client: Call.Factory = ReconnectingWebSocket.sharedHttpClient,
    private val scheduler: Scheduler = AndroidHandlerScheduler(),
    private val elapsed: () -> Long = android.os.SystemClock::elapsedRealtime,
    private val publish: (PublicChart) -> Unit,
) {
    var state = PublicChart("1m")
        private set
    private var visible = false
    private var pending: Call? = null
    private var limitedUntil = 0L

    fun select(interval: String) {
        require(interval in chartIntervals)
        if (state.interval == interval) return
        cancel()
        state = PublicChart(interval)
        publish(state)
        if (visible) fetch()
    }

    fun setVisible(show: Boolean) {
        if (visible == show) return
        visible = show
        if (show) {
            publish(state)
            fetch()
        } else {
            cancel()
            state = state.copy(fresh = false, status = "Menunggu pembaruan")
        }
    }

    private fun cancel() {
        scheduler.cancelAll()
        pending?.cancel()
        pending = null
    }

    private fun fetch() {
        if (!visible || pending != null) return
        val remaining = limitedUntil - elapsed()
        if (remaining > 0) {
            state = state.copy(status = "Batas permintaan Binance · menunggu", fresh = false)
            publish(state)
            scheduler.postDelayed(remaining, ::fetch)
            return
        }
        val interval = state.interval
        val request = Request.Builder()
            .url("https://fapi.binance.com/fapi/v1/klines?symbol=BTCUSDT&interval=$interval&limit=61").build()
        val call = client.newCall(request)
        pending = call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = complete(call, "Koneksi grafik terputus")

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        val cooldown = if (it.code in listOf(418, 429))
                            (it.header("Retry-After")?.toLongOrNull() ?: 60L).coerceIn(1, 86400) * 1000 else 0L
                        complete(call, "Grafik ditolak Binance (HTTP ${it.code})", cooldown = cooldown)
                        return
                    }
                    try {
                        val bars = parse(it.body.string())
                        complete(call, "Candle terbaru dapat berubah", bars, it.headers.getDate("Date")?.time)
                    } catch (_: Exception) {
                        complete(call, "Data candle belum dapat dibaca")
                    }
                }
            }
        })
    }

    private fun complete(call: Call, message: String, bars: List<Candle>? = null, at: Long? = null, cooldown: Long = 0) {
        scheduler.post {
            if (pending !== call || !visible) return@post
            pending = null
            val usable = bars != null && bars.last().openTime >= (state.bars.lastOrNull()?.openTime ?: 0)
            state = if (usable) PublicChart(state.interval, requireNotNull(bars), message, true, at ?: System.currentTimeMillis())
                else state.copy(fresh = false, status = if (bars == null) message else "Menunggu candle terbaru")
            publish(state)
            limitedUntil = elapsed() + cooldown
            val delay = when {
                cooldown > 0 -> cooldown
                !usable -> 30_000L
                state.interval == "1s" -> 2000L
                else -> 10_000L
            }
            scheduler.postDelayed(delay, ::fetch)
        }
    }

    companion object {
        internal fun parse(body: String): List<Candle> {
            val rows = JSONArray(body)
            require(rows.length() in 1..61)
            var lastEnd = -1L
            return List(rows.length()) { index ->
                val row = rows.getJSONArray(index)
                val candle = Candle.fromKline(row)
                val end = row.getLong(6)
                require(candle.openTime >= 0 && end >= candle.openTime)
                require(index == 0 || candle.openTime == lastEnd + 1)
                lastEnd = end
                candle
            }
        }
    }
}
