package com.mefuturesaibot.app

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.mefuturesaibot.app.exchange.ReconnectingWebSocket
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

/** One public depth snapshot per tap. Only the visible dashboard holds the result. */
internal class DashboardDepth(activity: Activity) {
    private val load = activity.findViewById<Button>(R.id.dash_depth_load)
    private val stamp = activity.findViewById<TextView>(R.id.dash_depth_time)
    private val buy = activity.findViewById<TextView>(R.id.dash_depth_bids)
    private val sell = activity.findViewById<TextView>(R.id.dash_depth_asks)
    private val main = Handler(Looper.getMainLooper())
    private var call: Call? = null
    private var retryAt = 0L

    init { load.setOnClickListener { fetch() } }

    private fun fetch() {
        if (call != null) return
        stamp.visibility = View.VISIBLE
        if (SystemClock.elapsedRealtime() < retryAt) {
            stamp.text = "Tunggu sejenak sebelum memuat ulang."
            return
        }
        retryAt = SystemClock.elapsedRealtime() + 3000
        stamp.text = "Memuat kedalaman pasar…"
        buy.text = ""
        sell.text = ""
        load.isEnabled = false
        val request = Request.Builder().url("https://fapi.binance.com/fapi/v1/depth?symbol=BTCUSDT&limit=5").build()
        val pending = ReconnectingWebSocket.sharedHttpClient.newCall(request)
        call = pending
        pending.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = finish(call, "Koneksi gagal. Coba muat ulang.")

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        val delay = if (it.code == 429 || it.code == 418)
                            (it.header("Retry-After")?.toLongOrNull() ?: 60L).coerceIn(1, 86400) else 0
                        finish(call, "Kedalaman belum tersedia (HTTP ${it.code}).", pauseSeconds = delay)
                        return
                    }
                    try {
                        val data = JSONObject(it.body.string())
                        val instant = data.getLong("T")
                        require(instant > 0)
                        val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(instant))
                        fun levels(side: String): String {
                            val rows = data.getJSONArray(side)
                            return (0 until minOf(5, rows.length())).joinToString("\n") { index ->
                                val entry = rows.getJSONArray(index)
                                "${entry.getString(0)}  ×  ${entry.getString(1)}"
                            }.ifEmpty { "—" }
                        }
                        finish(call, "Snapshot bursa · $time\nHarga USDT × jumlah BTC", levels("bids"), levels("asks"))
                    } catch (_: Exception) {
                        finish(call, "Snapshot belum tersedia. Coba muat ulang.")
                    }
                }
            }
        })
    }

    private fun finish(source: Call, message: String, bids: String = "", asks: String = "", pauseSeconds: Long = 0) {
        main.post {
            if (call !== source) return@post
            call = null
            if (pauseSeconds > 0) retryAt = SystemClock.elapsedRealtime() + pauseSeconds * 1000
            stamp.text = message
            buy.text = bids
            sell.text = asks
            load.isEnabled = true
        }
    }

    fun clear() {
        call?.cancel()
        call = null
        stamp.visibility = View.GONE
        buy.text = ""
        sell.text = ""
        load.isEnabled = true
    }
}
