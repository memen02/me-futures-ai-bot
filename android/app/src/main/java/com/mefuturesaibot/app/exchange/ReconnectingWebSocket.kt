package com.mefuturesaibot.app.exchange

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Wrapper WebSocket dengan AUTO-RECONNECT (backoff eksponensial + jitter) -- versi
 * Kotlin dari referensi Node.js `backend/src/exchange/wsClient.js`. Ini FASE
 * sekarang: reconnect selama proses app hidup (dipanggil dari Activity). Supaya
 * tetap reconnect walau app ditutup/layar mati, perlu dipindah ke dalam Foreground
 * Service -- itu increment BERIKUTNYA, belum di sini (lihat docs/TODO.md).
 *
 * Callback `onStatusChange`/`onMessage` SELALU dipanggil di main thread Android,
 * supaya aman dipakai langsung untuk update UI tanpa runOnUiThread manual di
 * pemanggil (OkHttp sendiri memanggil listener-nya di thread background).
 */
class ReconnectingWebSocket(
    private val url: String,
    // PERBAIKAN (audit 2026-11-08, temuan #5): dulu default `OkHttpClient()` di sini
    // membuat INSTANCE BARU setiap kali ReconnectingWebSocket dipanggil tanpa `client`
    // eksplisit -- OkHttp dirancang dipakai sebagai singleton (menyimpan thread pool &
    // connection pool sendiri), jadi keluar-masuk app berulang menumpuk thread yang
    // tidak perlu. Sekarang pakai `sharedHttpClient` (lazy, satu instance untuk seluruh
    // proses app). Lihat OkHttpSingletonTest.kt untuk buktinya.
    private val client: OkHttpClient = sharedHttpClient,
    private val onStatusChange: (ConnectionStatus) -> Unit = {},
    private val onMessage: (String) -> Unit = {},
) {
    enum class ConnectionStatus { CONNECTING, CONNECTED, DISCONNECTED, RECONNECTING }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webSocket: WebSocket? = null
    private var attempt = 0
    private var closedByUser = false

    fun connect() {
        closedByUser = false
        postStatus(if (attempt == 0) ConnectionStatus.CONNECTING else ConnectionStatus.RECONNECTING)

        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    attempt = 0
                    postStatus(ConnectionStatus.CONNECTED)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    mainHandler.post { onMessage(text) }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    postStatus(ConnectionStatus.DISCONNECTED)
                    if (!closedByUser) scheduleReconnect()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    postStatus(ConnectionStatus.DISCONNECTED)
                    if (!closedByUser) scheduleReconnect()
                }
            },
        )
    }

    private fun scheduleReconnect() {
        val delayMs = computeBackoffMs(attempt)
        attempt += 1
        postStatus(ConnectionStatus.RECONNECTING)
        mainHandler.postDelayed({ connect() }, delayMs)
    }

    private fun postStatus(status: ConnectionStatus) {
        mainHandler.post { onStatusChange(status) }
    }

    /** Panggil ini saat Activity/Service berhenti, supaya tidak reconnect terus setelah sengaja ditutup. */
    fun close() {
        closedByUser = true
        webSocket?.close(1000, "Ditutup oleh pengguna")
    }

    companion object {
        /**
         * OkHttpClient dibagi bersama untuk SELURUH proses app (lazy -- baru dibuat
         * saat pertama kali dipakai). Dipublikasikan (bukan private) supaya bisa
         * diperiksa langsung dari unit test bahwa ini benar-benar satu instance yang
         * sama, bukan sekadar diklaim di komentar. Lihat OkHttpSingletonTest.kt.
         */
        val sharedHttpClient: OkHttpClient by lazy { OkHttpClient() }

        /**
         * Backoff eksponensial + jitter, dibatasi maxMs -- FUNGSI MURNI (tidak
         * menyentuh Android API apa pun), diuji penuh lewat JUnit biasa tanpa
         * perlu membuat instance ReconnectingWebSocket. Lihat BackoffCalculatorTest.kt.
         */
        fun computeBackoffMs(attempt: Int, baseMs: Long = 1000, maxMs: Long = 30_000): Long {
            val exp = min(maxMs.toDouble(), baseMs * 2.0.pow(attempt))
            val jitter = Random.nextDouble() * 0.3 * exp
            return (exp + jitter).toLong()
        }
    }
}
