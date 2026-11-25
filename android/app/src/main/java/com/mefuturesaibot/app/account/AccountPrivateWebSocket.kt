package com.mefuturesaibot.app.account

import com.mefuturesaibot.app.exchange.AndroidHandlerScheduler
import com.mefuturesaibot.app.exchange.ReconnectingWebSocket
import com.mefuturesaibot.app.exchange.Scheduler
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
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
 * Network listeners parse messages before enqueueing.
 * The scheduler must be dedicated to this transport
 * and must enqueue callbacks asynchronously.
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

    private val queueLock = Any()

    private var pendingCallbacks = 0

    private var terminalQueued = false

    private var terminalFailure: AccountSocketFailure? = null

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
                        if (closed) return

                        if (text.length > MAX_MESSAGE_LENGTH) {
                            queueFailure(
                                current,
                                webSocket,
                                AccountSocketFailure()
                            )
                            return
                        }

                        if (text.isEmpty()) return

                        val event =
                            AccountStreamEventParser.parse(text)

                        if (
                            event.type == AccountStreamEventType.INVALID ||
                            event.type == AccountStreamEventType.IGNORED
                        ) return

                        dispatch(current, webSocket) {
                            if (connected) {
                                handleEvent(event)
                            }
                        }
                    }

                    override fun onClosing(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String
                    ) {
                        queueFailure(
                            current,
                            webSocket,
                            AccountSocketFailure()
                        )
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String
                    ) {
                        queueFailure(
                            current,
                            webSocket,
                            AccountSocketFailure()
                        )
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

                        queueFailure(
                            current,
                            webSocket,
                            failure
                        )
                    }
                }
            )

            scheduler.postDelayed(CONNECT_TIMEOUT_MS) {
                if (
                    !closed &&
                    generation == current &&
                    !connected
                ) {
                    val failure = synchronized(queueLock) {
                        terminalFailure
                    }

                    disconnect(
                        failure ?: AccountSocketFailure()
                    )
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
        synchronized(queueLock) {
            if (closed || terminalQueued) return

            if (pendingCallbacks >= MAX_PENDING_CALLBACKS) {
                queueFailure(
                    current,
                    source,
                    AccountSocketFailure()
                )
                return
            }

            pendingCallbacks++

            scheduler.post {
                val allowed = synchronized(queueLock) {
                    pendingCallbacks =
                        (pendingCallbacks - 1).coerceAtLeast(0)

                    !closed && !terminalQueued
                }

                if (
                    allowed &&
                    generation == current &&
                    socket === source
                ) {
                    action()
                }
            }
        }
    }

    private fun queueFailure(
        current: Long,
        source: WebSocket,
        failure: AccountSocketFailure
    ) {
        synchronized(queueLock) {
            if (closed) return

            val previous = terminalFailure

            terminalFailure = preferFailure(
                previous,
                failure
            )

            if (terminalQueued) return

            terminalQueued = true

            scheduler.post {
                if (
                    !closed &&
                    generation == current &&
                    socket === source
                ) {
                    disconnect(
                        synchronized(queueLock) {
                            terminalFailure
                        } ?: AccountSocketFailure()
                    )
                }
            }
        }
    }

    private fun preferFailure(
        previous: AccountSocketFailure?,
        next: AccountSocketFailure
    ): AccountSocketFailure {
        if (previous == null) return next

        val oldDelay = AccountRetryAfter.delayMs(
            previous.httpStatus ?: 0,
            previous.retryAfter
        )

        val newDelay = AccountRetryAfter.delayMs(
            next.httpStatus ?: 0,
            next.retryAfter
        )

        return when {
            newDelay > oldDelay -> next
            oldDelay > 0L -> previous
            previous.httpStatus == null &&
                next.httpStatus != null -> next
            else -> previous
        }
    }

    private fun sanitizeRetryAfter(
        value: String?
    ): String? {
        val raw = value?.trim() ?: return null

        if (raw.isEmpty()) return null

        if (raw.all { it in '0'..'9' }) {
            return raw.toLongOrNull()?.toString()
                ?: Long.MAX_VALUE.toString()
        }

        if (raw.length > 128) return null

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

        val finalFailure = synchronized(queueLock) {
            val selected = preferFailure(
                terminalFailure,
                failure
            )

            closed = true
            selected
        }

        terminate()

        onStatus(AccountSocketStatus.DISCONNECTED)

        onFailure(finalFailure)
    }

    private fun terminate() {
        synchronized(queueLock) {
            closed = true
            pendingCallbacks = 0
            terminalQueued = false
            terminalFailure = null

            scheduler.cancelAll()
        }

        connected = false
        generation++

        val previous = socket

        socket = null

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
