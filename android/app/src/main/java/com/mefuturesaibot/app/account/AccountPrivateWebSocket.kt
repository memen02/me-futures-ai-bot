package com.mefuturesaibot.app.account

import com.mefuturesaibot.app.exchange.AndroidHandlerScheduler
import com.mefuturesaibot.app.exchange.ReconnectingWebSocket
import com.mefuturesaibot.app.exchange.Scheduler
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

internal enum class AccountSocketStatus {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    SESSION_EXPIRED
}

internal data class AccountSocketFailure(
    val httpStatus: Int? = null,
    val retryAfter: String? = null
)

/**
 * Single-session, display-only WebSocket transport.
 *
 * All public lifecycle methods and callbacks must
 * run on the same serial scheduler thread.
 *
 * Session manager owns reconnect and key renewal.
 */
internal class AccountPrivateWebSocket(
    private val request: Request,
    private val onStatus: (AccountSocketStatus) -> Unit,
    private val onEvent: (AccountStreamEvent) -> Unit,
    private val onFailure: (AccountSocketFailure) -> Unit,
    private val factory: WebSocket.Factory =
        ReconnectingWebSocket.sharedHttpClient
            .newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build(),
    private val scheduler: Scheduler =
        AndroidHandlerScheduler()
) : AutoCloseable {

    init {
        require(request.url.isHttps) {
            "Koneksi akun wajib menggunakan TLS"
        }

        require(request.method == "GET")

        require(request.headers.size == 0) {
            "Header tambahan tidak diizinkan"
        }

        require(
            request.url.username.isEmpty() &&
                request.url.password.isEmpty() &&
                request.url.fragment == null &&
                request.url.port == 443
        ) {
            "Format URL sesi tidak valid"
        }
    }

    private var socket: WebSocket? = null
    private var generation = 0L
    private var started = false
    @Volatile
    private var closed = false

    private var connected = false

    private val pendingCallbacks = AtomicInteger(0)

    private val overflowQueued = AtomicBoolean(false)

    fun connect() {
        check(!closed)

        if (started) return

        started = true
        val current = ++generation

        onStatus(AccountSocketStatus.CONNECTING)

        if (closed || generation != current) return

        try {
            socket = factory.newWebSocket(
                request,
                object : WebSocketListener() {

                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response
                    ) {
                        dispatch(current, webSocket) {
                            if (!connected) {
                                connected = true
                                onStatus(
                                    AccountSocketStatus.CONNECTED
                                )
                            }
                        }
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String
                    ) {
                        if (
                            closed ||
                            text.length !in 1..MAX_MESSAGE_LENGTH
                        ) return

                        dispatch(current, webSocket) {
                            if (connected) {
                                handleEvent(
                                    AccountStreamEventParser.parse(text)
                                )
                            }
                        }
                    }

                    override fun onClosing(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String
                    ) {
                        dispatch(current, webSocket) {
                            disconnect(
                                AccountSocketFailure()
                            )
                        }
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String
                    ) {
                        dispatch(current, webSocket) {
                            disconnect(
                                AccountSocketFailure()
                            )
                        }
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?
                    ) {
                        val failure = AccountSocketFailure(
                            httpStatus = response?.code,
                            retryAfter = sanitizeRetryAfter(
                                response?.header("Retry-After")
                            )
                        )

                        dispatch(current, webSocket) {
                            disconnect(failure)
                        }
                    }
                }
            )

            scheduler.postDelayed(CONNECT_TIMEOUT_MS) {
                if (
                    !closed &&
                    generation == current &&
                    !connected
                ) {
                    disconnect(AccountSocketFailure())
                }
            }

        } catch (_: Exception) {
            disconnect(AccountSocketFailure())
        }
    }

    private fun dispatch(
        current: Long,
        source: WebSocket,
        action: () -> Unit
    ) {
        if (closed || overflowQueued.get()) return

        val count = pendingCallbacks.incrementAndGet()

        if (count > MAX_PENDING_CALLBACKS) {
            pendingCallbacks.decrementAndGet()

            if (
                overflowQueued.compareAndSet(false, true)
            ) {
                scheduler.post {
                    if (
                        !closed &&
                        generation == current &&
                        socket === source
                    ) {
                        disconnect(AccountSocketFailure())
                    }
                }
            }

            return
        }

        scheduler.post {
            try {
                if (
                    !closed &&
                    !overflowQueued.get() &&
                    generation == current &&
                    socket === source
                ) {
                    action()
                }
            } finally {
                pendingCallbacks.decrementAndGet()
            }
        }
    }

    private fun sanitizeRetryAfter(
        value: String?
    ): String? {
        val raw = value?.trim() ?: return null

        if (raw.length !in 1..128) return null

        if (raw.all { it in '0'..'9' }) {
            return raw.toLongOrNull()?.toString()
                ?: Long.MAX_VALUE.toString()
        }

        return runCatching {
            ZonedDateTime.parse(
                raw,
                DateTimeFormatter.RFC_1123_DATE_TIME
            ).format(
                DateTimeFormatter.RFC_1123_DATE_TIME
            )
        }.getOrNull()
    }

    private fun handleEvent(event: AccountStreamEvent) {
        when (event.type) {

            AccountStreamEventType.INVALID,
            AccountStreamEventType.IGNORED -> Unit

            AccountStreamEventType.SESSION_EXPIRED -> {
                terminate()

                onStatus(
                    AccountSocketStatus.SESSION_EXPIRED
                )
            }

            else -> onEvent(event)
        }
    }

    private fun disconnect(failure: AccountSocketFailure) {
        if (closed) return

        terminate()

        onStatus(AccountSocketStatus.DISCONNECTED)

        onFailure(failure)
    }

    private fun terminate() {
        closed = true
        connected = false
        generation++

        val previous = socket

        socket = null

        scheduler.cancelAll()

        previous?.cancel()
    }

    override fun close() {
        if (closed) return

        terminate()
    }

    private companion object {
        const val MAX_MESSAGE_LENGTH = 262144
        const val MAX_PENDING_CALLBACKS = 32
        const val CONNECT_TIMEOUT_MS = 15000L
    }
}
