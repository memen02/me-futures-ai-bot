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
 * Abstraksi scheduler -- SENGAJA ditambahkan (audit 2026-11-10, temuan #2) supaya
 * logic reconnect/cancel bisa diuji penuh lewat JUnit biasa tanpa Android Looper
 * sungguhan. Implementasi default (AndroidHandlerScheduler) dipakai saat runtime;
 * test memakai scheduler palsu yang bisa dikontrol manual. Lihat
 * CloseCancelsReconnectTest.kt untuk bukti bug lama ini sudah benar-benar diperbaiki.
 */
interface Scheduler {
    fun post(action: () -> Unit)
    fun postDelayed(delayMs: Long, action: () -> Unit)
    /** Batalkan SEMUA post/postDelayed yang masih tertunda. */
    fun cancelAll()
}

class AndroidHandlerScheduler(looper: Looper = Looper.getMainLooper()) : Scheduler {
    private val handler = Handler(looper)
    override fun post(action: () -> Unit) {
        handler.post(action)
    }
    override fun postDelayed(delayMs: Long, action: () -> Unit) {
        handler.postDelayed(action, delayMs)
    }
    override fun cancelAll() {
        handler.removeCallbacksAndMessages(null)
    }
}

/**
 * Wrapper WebSocket dengan AUTO-RECONNECT (backoff eksponensial + jitter) -- versi
 * Kotlin dari referensi Node.js `backend/src/exchange/wsClient.js`.
 *
 * Callback `onStatusChange`/`onMessage` SELALU dipanggil lewat `scheduler`, supaya
 * aman dipakai langsung untuk update UI tanpa runOnUiThread manual di pemanggil
 * (OkHttp sendiri memanggil listener-nya di thread background).
 */
class ReconnectingWebSocket(
    private val url: String,
    // Singleton OkHttpClient (audit 2026-11-08 #5) -- lihat OkHttpSingletonTest.kt.
    private val client: OkHttpClient = sharedHttpClient,
    private val onStatusChange: (ConnectionStatus) -> Unit = {},
    private val onMessage: (String) -> Unit = {},
    private val scheduler: Scheduler = AndroidHandlerScheduler(),
) {
    enum class ConnectionStatus { CONNECTING, CONNECTED, DISCONNECTED, RECONNECTING }

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
                    scheduler.post { onMessage(text) }
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
        // PERBAIKAN (audit 2026-11-10 #2): dulu di sini langsung `{ connect() }` tanpa
        // syarat -- kalau close() dipanggil SETELAH reconnect ini terjadwal tapi
        // SEBELUM delay-nya habis, dulu connect() tetap jalan (bikin koneksi zombie).
        // Sekarang double-protected: scheduler.cancelAll() di close() membatalkan
        // callback ini sebelum sempat jalan, DAN kalaupun lolos (race condition),
        // guard closedByUser di bawah ini mencegahnya benar-benar connect ulang.
        scheduler.postDelayed(delayMs) {
            if (!closedByUser) connect()
        }
    }

    private fun postStatus(status: ConnectionStatus) {
        scheduler.post { onStatusChange(status) }
    }

    /**
     * Panggil saat Activity/Service berhenti. PERBAIKAN (audit 2026-11-10 #2):
     * sekarang membatalkan reconnect yang mungkin sudah terjadwal lewat
     * `scheduler.cancelAll()` -- dulu tidak, lihat catatan di scheduleReconnect().
     */
    fun close() {
        closedByUser = true
        scheduler.cancelAll()
        webSocket?.close(1000, "Ditutup oleh pengguna")
    }

    companion object {
        /**
         * OkHttpClient dibagi bersama untuk SELURUH proses app (lazy). Dipublikasikan
         * (bukan private) supaya bisa diperiksa langsung dari unit test. Lihat
         * OkHttpSingletonTest.kt.
         */
        val sharedHttpClient: OkHttpClient by lazy { OkHttpClient() }

        /**
         * Backoff eksponensial + jitter, dibatasi maxMs -- FUNGSI MURNI, diuji penuh
         * lewat JUnit biasa. Lihat BackoffCalculatorTest.kt.
         */
        fun computeBackoffMs(attempt: Int, baseMs: Long = 1000, maxMs: Long = 30_000): Long {
            val exp = min(maxMs.toDouble(), baseMs * 2.0.pow(attempt))
            val jitter = Random.nextDouble() * 0.3 * exp
            return (exp + jitter).toLong()
        }
    }
}
