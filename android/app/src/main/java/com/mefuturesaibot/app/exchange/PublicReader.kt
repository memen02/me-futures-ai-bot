package com.mefuturesaibot.app.exchange

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/** One visible public resource. Cancellation also invalidates callbacks already queued on the UI thread. */
internal class PublicReader(private val demo: Boolean = false) {
    private val main = Handler(Looper.getMainLooper())
    private var pending: Call? = null
    fun read(path: String, done: (String?, Long?, String) -> Unit) {
        cancel()
        if (SystemClock.elapsedRealtime() < retryAt) {
            done(null, null, "Batas permintaan Binance")
            return
        }
        val host = if (demo) "demo-fapi.binance.com" else "fapi.binance.com"
        val request = Request.Builder().url("https://$host/fapi/v1/$path").build()
        val current = ReconnectingWebSocket.sharedHttpClient.newCall(request)
        pending = current
        fun finish(body: String?, at: Long?, note: String) {
            main.post {
                if (pending === current) {
                    pending = null
                    done(body, at, note)
                }
            }
        }
        current.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = finish(null, null, "Koneksi terputus")
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.code == 429 || it.code == 418) {
                        val seconds = (it.header("Retry-After")?.toLongOrNull() ?: 60).coerceIn(1, 86400)
                        retryAt = maxOf(retryAt, SystemClock.elapsedRealtime() + seconds * 1000)
                    }
                    try {
                        if (it.isSuccessful) finish(it.body.string(), it.headers.getDate("Date")?.time, "")
                        else finish(null, null, "Binance HTTP ${it.code}")
                    } catch (_: IOException) { finish(null, null, "Respons Binance terputus") }
                }
            }
        })
    }
    fun cancel() { pending?.cancel(); pending = null }
    companion object {
        @Volatile private var retryAt = 0L
        fun encode(symbol: String): String = java.net.URLEncoder.encode(symbol, "UTF-8")
    }
}
