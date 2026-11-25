package com.mefuturesaibot.app.account

import com.mefuturesaibot.app.exchange.Scheduler
import java.io.IOException
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.Assert.*
import org.junit.Test

class AccountPrivateWebSocketTest {

    private class TestScheduler : Scheduler {

        val ready = mutableListOf<() -> Unit>()
        val delayed = mutableListOf<() -> Unit>()

        override fun post(action: () -> Unit) {
            ready.add(action)
        }

        override fun postDelayed(
            delayMs: Long,
            action: () -> Unit
        ) {
            delayed.add(action)
        }

        override fun cancelAll() {
            ready.clear()
            delayed.clear()
        }

        fun drain() {
            while (ready.isNotEmpty()) {
                ready.removeAt(0).invoke()
            }
        }
    }

    private class TestSocket(
        private val original: Request,
        val listener: WebSocketListener
    ) : WebSocket {

        var cancelled = false

        override fun request() = original

        override fun queueSize() = 0L

        override fun send(text: String): Boolean =
            error("Unexpected send")

        override fun send(bytes: ByteString): Boolean =
            error("Unexpected send")

        override fun close(
            code: Int,
            reason: String?
        ): Boolean {
            cancelled = true
            return true
        }

        override fun cancel() {
            cancelled = true
        }

        fun open() {
            val response = Response.Builder()
                .request(original)
                .protocol(Protocol.HTTP_1_1)
                .code(101)
                .message("Switching Protocols")
                .build()

            listener.onOpen(this, response)
        }

        fun message(text: String) {
            listener.onMessage(this, text)
        }

        fun fail(
            status: Int? = null,
            retryAfter: String? = null
        ) {
            val response = status?.let {
                val builder = Response.Builder()
                    .request(original)
                    .protocol(Protocol.HTTP_1_1)
                    .code(it)
                    .message("Mock failure")

                if (retryAfter != null) {
                    builder.header(
                        "Retry-After",
                        retryAfter
                    )
                }

                builder.build()
            }

            listener.onFailure(
                this,
                IOException("fake-sensitive-value"),
                response
            )
        }
    }

    private val scheduler = TestScheduler()

    private val sockets =
        mutableListOf<TestSocket>()

    private val statuses =
        mutableListOf<AccountSocketStatus>()

    private val events =
        mutableListOf<AccountStreamEvent>()

    private val failures =
        mutableListOf<AccountSocketFailure>()

    private val request = Request.Builder()
        .url("wss://example.test/private/ws/fake-key")
        .build()

    private val factory = object : WebSocket.Factory {
        override fun newWebSocket(
            request: Request,
            listener: WebSocketListener
        ): WebSocket {
            return TestSocket(
                request,
                listener
            ).also {
                sockets.add(it)
            }
        }
    }

    private fun connection(
        request: Request = this.request,
        factory: WebSocket.Factory = this.factory
    ): AccountPrivateWebSocket {
        return AccountPrivateWebSocket(
            request = request,
            onStatus = { statuses.add(it) },
            onEvent = { events.add(it) },
            onFailure = { failures.add(it) },
            factory = factory,
            scheduler = scheduler
        )
    }

    private fun openConnection(
        connection: AccountPrivateWebSocket
    ): TestSocket {
        connection.connect()

        return sockets.single().also {
            it.open()
            scheduler.drain()
        }
    }

    private val accountEvent =
        """{"e":"ACCOUNT_UPDATE","E":1700000000000,"a":{}}"""

    private val orderEvent =
        """{"e":"ORDER_TRADE_UPDATE","E":1700000000000,"o":{}}"""

    private val configEvent =
        """{"e":"ACCOUNT_CONFIG_UPDATE","E":1700000000000}"""

    private val expiredEvent =
        """{"e":"listenKeyExpired","E":1700000000000}"""

    @Test
    fun connectsAndReportsStatus() {
        connection().use { session ->
            openConnection(session)

            assertEquals(
                listOf(
                    AccountSocketStatus.CONNECTING,
                    AccountSocketStatus.CONNECTED
                ),
                statuses
            )

            assertTrue(failures.isEmpty())
        }
    }

    @Test
    fun duplicateConnectCreatesOnlyOneSocket() {
        connection().use {
            it.connect()
            it.connect()
            it.connect()

            assertEquals(1, sockets.size)
            assertEquals(1, statuses.size)
        }
    }

    @Test
    fun duplicateOpenDoesNotRepeatStatus() {
        connection().use {
            val socket = openConnection(it)

            socket.open()
            scheduler.drain()

            assertEquals(2, statuses.size)
        }
    }

    @Test
    fun deliversAccountEvent() {
        connection().use {
            val socket = openConnection(it)

            socket.message(accountEvent)
            scheduler.drain()

            assertEquals(
                AccountStreamEventType.ACCOUNT_CHANGED,
                events.single().type
            )

            assertEquals(
                1700000000000L,
                events.single().eventTime
            )
        }
    }

    @Test
    fun deliversOrderEvent() {
        connection().use {
            val socket = openConnection(it)

            socket.message(orderEvent)
            scheduler.drain()

            assertEquals(
                AccountStreamEventType.ORDER_CHANGED,
                events.single().type
            )
        }
    }

    @Test
    fun deliversConfigEvent() {
        connection().use {
            val socket = openConnection(it)

            socket.message(configEvent)
            scheduler.drain()

            assertEquals(
                AccountStreamEventType.CONFIG_CHANGED,
                events.single().type
            )
        }
    }

    @Test
    fun ignoresMalformedAndUnknownEvents() {
        connection().use {
            val socket = openConnection(it)

            socket.message("{broken")
            socket.message("""{"e":"bookTicker"}""")
            socket.message("")
            scheduler.drain()

            assertTrue(events.isEmpty())
            assertTrue(failures.isEmpty())
        }
    }

    @Test
    fun ignoresEventsBeforeOpen() {
        connection().use {
            it.connect()

            sockets.single().message(accountEvent)

            scheduler.drain()

            assertTrue(events.isEmpty())
        }
    }

    @Test
    fun expiredSessionCancelsSocket() {
        connection().use {
            val socket = openConnection(it)

            socket.message(expiredEvent)
            scheduler.drain()

            assertTrue(socket.cancelled)

            assertEquals(
                AccountSocketStatus.SESSION_EXPIRED,
                statuses.last()
            )

            assertTrue(events.isEmpty())
            assertTrue(failures.isEmpty())
        }
    }

    @Test
    fun expiredSessionRejectsLaterEvents() {
        connection().use {
            val socket = openConnection(it)

            socket.message(expiredEvent)
            socket.message(accountEvent)

            scheduler.drain()

            socket.message(orderEvent)
            scheduler.drain()

            assertTrue(events.isEmpty())
        }
    }

    @Test
    fun forwardsHttpRateLimitMetadata() {
        connection().use {
            it.connect()

            sockets.single().fail(429, "180")

            scheduler.drain()

            assertEquals(
                AccountSocketStatus.DISCONNECTED,
                statuses.last()
            )

            assertEquals(
                AccountSocketFailure(429, "180"),
                failures.single()
            )
        }
    }

    @Test
    fun networkFailureDoesNotLeakException() {
        connection().use {
            val socket = openConnection(it)

            socket.fail()
            scheduler.drain()

            assertEquals(
                AccountSocketFailure(),
                failures.single()
            )

            assertFalse(
                failures.single().toString()
                    .contains("fake-sensitive-value")
            )
        }
    }

    @Test
    fun failureDoesNotAutomaticallyReconnect() {
        connection().use {
            val socket = openConnection(it)

            socket.fail()
            scheduler.drain()

            assertEquals(1, sockets.size)
            assertTrue(scheduler.delayed.isEmpty())
            assertTrue(socket.cancelled)
        }
    }

    @Test
    fun duplicateTerminalCallbacksReportedOnce() {
        connection().use {
            val socket = openConnection(it)

            socket.fail()

            socket.listener.onClosed(
                socket,
                1000,
                "closed"
            )

            scheduler.drain()

            socket.fail()
            scheduler.drain()

            assertEquals(1, failures.size)

            assertEquals(
                1,
                statuses.count {
                    it == AccountSocketStatus.DISCONNECTED
                }
            )
        }
    }

    @Test
    fun closeDiscardsQueuedAndLateEvents() {
        val session = connection()

        val socket = openConnection(session)

        socket.message(accountEvent)

        val before = statuses.toList()

        session.close()

        socket.message(orderEvent)
        socket.fail()
        scheduler.drain()

        assertTrue(socket.cancelled)
        assertTrue(events.isEmpty())
        assertTrue(failures.isEmpty())
        assertEquals(before, statuses)
    }

    @Test
    fun closeRejectsAlreadyDequeuedCallback() {
        val session = connection()

        val socket = openConnection(session)

        socket.message(accountEvent)

        val callback = scheduler.ready.single()

        session.close()

        callback()

        assertTrue(events.isEmpty())
    }

    @Test
    fun closeIsPermanentAndIdempotent() {
        val session = connection()

        session.close()
        session.close()

        assertThrows(
            IllegalStateException::class.java
        ) {
            session.connect()
        }

        assertTrue(sockets.isEmpty())
    }

    @Test
    fun rejectsUnencryptedRequest() {
        val unsafe = Request.Builder()
            .url("ws://example.test/ws/fake-key")
            .build()

        assertThrows(
            IllegalArgumentException::class.java
        ) {
            connection(request = unsafe)
        }
    }

    @Test
    fun rejectsAuthenticationHeaders() {
        for (header in listOf(
            "Authorization",
            "X-MBX-APIKEY"
        )) {
            val unsafe = request.newBuilder()
                .header(header, "fake-secret")
                .build()

            assertThrows(
                IllegalArgumentException::class.java
            ) {
                connection(request = unsafe)
            }
        }
    }

    @Test
    fun factoryFailureIsSanitized() {
        val broken = object : WebSocket.Factory {
            override fun newWebSocket(
                request: Request,
                listener: WebSocketListener
            ): WebSocket {
                throw IllegalStateException(
                    "fake-sensitive-value"
                )
            }
        }

        connection(factory = broken).use {
            it.connect()

            assertEquals(
                AccountSocketStatus.DISCONNECTED,
                statuses.last()
            )

            assertEquals(
                AccountSocketFailure(),
                failures.single()
            )
        }
    }

    @Test
    fun oversizedMessageNeverEntersQueue() {
        connection().use {
            val socket = openConnection(it)

            socket.message("x".repeat(262145))

            assertTrue(scheduler.ready.isEmpty())
            assertTrue(events.isEmpty())
        }
    }

    @Test
    fun lateCallbacksNeverEnterQueue() {
        val session = connection()

        val socket = openConnection(session)

        session.close()

        repeat(100) {
            socket.message(accountEvent)
            socket.fail()
        }

        assertTrue(scheduler.ready.isEmpty())
        assertTrue(events.isEmpty())
        assertTrue(failures.isEmpty())
    }

    @Test
    fun callbackOverflowTerminatesSession() {
        connection().use {
            val socket = openConnection(it)

            repeat(1000) {
                socket.message(accountEvent)
            }

            assertTrue(scheduler.ready.size <= 33)

            scheduler.drain()

            assertTrue(socket.cancelled)
            assertTrue(events.isEmpty())
            assertEquals(1, failures.size)

            assertEquals(
                AccountSocketStatus.DISCONNECTED,
                statuses.last()
            )
        }
    }

    @Test
    fun connectionTimeoutCancelsSocket() {
        connection().use {
            it.connect()

            val socket = sockets.single()

            scheduler.delayed.single().invoke()

            assertTrue(socket.cancelled)

            assertEquals(
                AccountSocketStatus.DISCONNECTED,
                statuses.last()
            )

            assertEquals(1, failures.size)
        }
    }

    @Test
    fun connectionTimeoutIgnoresConnectedSocket() {
        connection().use {
            val socket = openConnection(it)

            scheduler.delayed.single().invoke()

            assertFalse(socket.cancelled)
            assertTrue(failures.isEmpty())

            assertEquals(
                AccountSocketStatus.CONNECTED,
                statuses.last()
            )
        }
    }

    @Test
    fun closeInvalidatesDequeuedTimeout() {
        val session = connection()

        session.connect()

        val timeout = scheduler.delayed.single()

        session.close()

        timeout()

        assertTrue(failures.isEmpty())
        assertTrue(sockets.single().cancelled)
    }

    @Test
    fun rejectsCookieAndHostHeaders() {
        for (header in listOf("Cookie", "Host")) {
            val unsafe = request.newBuilder()
                .header(header, "fake-sensitive-value")
                .build()

            assertThrows(
                IllegalArgumentException::class.java
            ) {
                connection(request = unsafe)
            }
        }
    }

    @Test
    fun rejectsCredentialsAndUnsafePortInUrl() {
        val urls = listOf(
            "wss://user:pass@example.test/ws/key",
            "wss://example.test:8443/ws/key",
            "wss://example.test/ws/key#fragment"
        )

        for (url in urls) {
            val unsafe = Request.Builder()
                .url(url)
                .build()

            assertThrows(
                IllegalArgumentException::class.java
            ) {
                connection(request = unsafe)
            }
        }
    }

    @Test
    fun invalidRetryAfterDoesNotLeakSensitiveText() {
        connection().use {
            it.connect()

            sockets.single().fail(
                429,
                "fake-sensitive-value"
            )

            scheduler.drain()

            assertEquals(
                AccountSocketFailure(429, null),
                failures.single()
            )
        }
    }

    @Test
    fun oversizedNumericRetryAfterSaturates() {
        connection().use {
            it.connect()

            sockets.single().fail(
                429,
                "9".repeat(40)
            )

            scheduler.drain()

            assertEquals(
                AccountSocketFailure(
                    429,
                    Long.MAX_VALUE.toString()
                ),
                failures.single()
            )
        }
    }
}
