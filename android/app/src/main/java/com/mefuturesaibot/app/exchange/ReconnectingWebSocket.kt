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
    private val client: WebSocket.Factory = sharedHttpClient,
    private val onStatusChange: (ConnectionStatus) -> Unit = {},
    private val onMessage: (String) -> Unit = {},
    private val scheduler: Scheduler = AndroidHandlerScheduler(),
    private val messageBudget: MessageBudget? = null,
) {
    /** Limits retained message text before the scheduler is able to process it. */
    data class MessageBudget(val count: Int, val totalChars: Int) {
        init { require(count > 0 && totalChars > 0) }
    }

    private class Inbox(private val budget: MessageBudget) {
        private var count = 0
        private var chars = 0
        @Volatile var invalid = false
            private set

        @Synchronized fun offer(size: Int): Boolean {
            if (invalid) return false
            if (count >= budget.count || size > budget.totalChars - chars) {
                invalid = true
                return false
            }
            count++
            chars += size
            return true
        }

        @Synchronized fun consumed(size: Int) {
            count--
            chars -= size
        }
        fun invalidate() { invalid = true }
    }

    @Volatile private var currentInbox: Inbox? = null
    fun hasMessageGap() = currentInbox?.invalid == true

    enum class ConnectionStatus { CONNECTING, CONNECTED, DISCONNECTED, RECONNECTING }

    private var webSocket: WebSocket? = null
    private var attempt = 0
    // connect(), close() dan semua mutasi status berjalan di thread utama Service.
    // Listener OkHttp hanya mengantrekan pekerjaan ke thread tersebut.
    private var session = 0L

    fun connect() {
        if (webSocket != null) return
        scheduler.cancelAll()
        val currentSession = ++session
        onStatusChange(if (attempt == 0) ConnectionStatus.CONNECTING else ConnectionStatus.RECONNECTING)

        val inbox = messageBudget?.let(::Inbox)
        currentInbox = inbox
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                withActiveSocket(webSocket) {
                    attempt = 0
                    onStatusChange(ConnectionStatus.CONNECTED)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (inbox == null) {
                    withActiveSocket(webSocket) { onMessage(text) }
                    return
                }
                if (inbox.invalid) return
                if (!inbox.offer(text.length)) {
                    // Dropping a depth delta breaks continuity. Reconnect for a fresh snapshot.
                    disconnected(webSocket)
                    webSocket.cancel()
                    return
                }
                withActiveSocket(webSocket) {
                    try {
                        if (!inbox.invalid) onMessage(text)
                    } finally {
                        inbox.consumed(text.length)
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                disconnected(webSocket)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                disconnected(webSocket)
            }

            private fun disconnected(socket: WebSocket) {
                inbox?.invalidate()
                withActiveSocket(socket) {
                    this@ReconnectingWebSocket.webSocket = null
                    onStatusChange(ConnectionStatus.DISCONNECTED)
                    val delayMs = computeBackoffMs(attempt++)
                    onStatusChange(ConnectionStatus.RECONNECTING)
                    scheduler.postDelayed(delayMs) {
                        if (session == currentSession) connect()
                    }
                }
            }
        })
    }

    private fun withActiveSocket(source: WebSocket, action: () -> Unit) {
        scheduler.post {
            if (webSocket === source) action()
        }
    }

    fun close() {
        currentInbox?.invalidate()
        session++
        attempt = 0
        val closing = webSocket
        webSocket = null
        scheduler.cancelAll()
        closing?.close(1000, "Ditutup oleh pengguna")
    }

    companion object {
        const val BTC_BOOK_TICKER_URL = "wss://fstream.binance.com/public/stream?streams=btcusdt@bookTicker"

        /**
         * OkHttpClient dibagi bersama untuk SELURUH proses app (lazy). Dipublikasikan
         * (bukan private) supaya bisa diperiksa langsung dari unit test. Lihat
         * OkHttpSingletonTest.kt.
         */
        val sharedHttpClient: OkHttpClient by lazy { OkHttpClient.Builder().pingInterval(20, java.util.concurrent.TimeUnit.SECONDS).build() }

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
