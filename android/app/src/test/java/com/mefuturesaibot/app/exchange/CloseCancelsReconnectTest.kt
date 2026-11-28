package com.mefuturesaibot.app.exchange

import java.io.IOException
import okhttp3.Request
import okhttp3.Response
import okhttp3.Protocol
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class ManualScheduler : Scheduler {
    val ready = mutableListOf<() -> Unit>()
    val delayed = mutableListOf<() -> Unit>()
    override fun post(action: () -> Unit) { ready.add(action) }
    override fun postDelayed(delayMs: Long, action: () -> Unit) { delayed.add(action) }
    override fun cancelAll() {
        ready.clear()
        delayed.clear()
    }
    fun deliverCallbacks() {
        while (ready.isNotEmpty()) ready.removeAt(0).invoke()
    }
}

private class TestSocket(private val request: Request, val listener: WebSocketListener) : WebSocket {
    var closed = false
    override fun request() = request
    override fun queueSize() = 0L
    override fun send(text: String) = error("Tidak dipakai")
    override fun send(bytes: ByteString) = error("Tidak dipakai")
    override fun close(code: Int, reason: String?): Boolean {
        closed = true
        return true
    }
    override fun cancel() { closed = true }

    fun opened() {
        val response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(101).message("Switching Protocols").build()
        listener.onOpen(this, response)
    }
    fun fail() = listener.onFailure(this, IOException("putus"), null)
}

class CloseCancelsReconnectTest {
    private val scheduler = ManualScheduler()
    private val sockets = mutableListOf<TestSocket>()
    private val statuses = mutableListOf<ReconnectingWebSocket.ConnectionStatus>()
    private val messages = mutableListOf<String>()
    private val connection = ReconnectingWebSocket(
        url = "wss://example.test/stream",
        client = object : WebSocket.Factory {
            override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket =
                TestSocket(request, listener).also { sockets.add(it) }
        },
        scheduler = scheduler,
        onStatusChange = { statuses.add(it) },
        onMessage = { messages.add(it) },
    )

    @Test
    fun depthOverflowInvalidatesQueuedDeltasAndReconnectsWithAFreshBudget() {
        val bounded = ReconnectingWebSocket(
            url = "wss://example.test/depth",
            client = object : WebSocket.Factory {
                override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket =
                    TestSocket(request, listener).also(sockets::add)
            },
            scheduler = scheduler,
            onMessage = { messages.add(it) },
            messageBudget = ReconnectingWebSocket.MessageBudget(count = 2, totalChars = 6)
        )
        bounded.connect()
        val first = sockets.single()
        first.listener.onMessage(first, "abc")
        first.listener.onMessage(first, "def")
        first.listener.onMessage(first, "g")
        repeat(100) { first.listener.onMessage(first, "late") }
        assertTrue(bounded.hasMessageGap())
        assertTrue(first.closed)
        assertEquals(3, scheduler.ready.size)
        scheduler.deliverCallbacks()
        assertTrue(messages.isEmpty())
        scheduler.delayed.single().invoke()
        val second = sockets.last()
        assertTrue(!bounded.hasMessageGap())
        second.listener.onMessage(second, "123456")
        scheduler.deliverCallbacks()
        second.listener.onMessage(second, "789")
        scheduler.deliverCallbacks()
        assertEquals(listOf("123456", "789"), messages)
        second.listener.onMessage(second, "toolarge")
        assertTrue(bounded.hasMessageGap())
        bounded.close()
        scheduler.deliverCallbacks()
        assertTrue(scheduler.delayed.isEmpty())
    }

    @Test
    fun stopIgnoresQueuedAndLateCallbacks() {
        connection.connect()
        val socket = sockets.single()
        socket.opened()
        scheduler.deliverCallbacks()
        assertEquals(ReconnectingWebSocket.ConnectionStatus.CONNECTED, statuses.last())
        socket.listener.onMessage(socket, "queued")
        val beforeStop = statuses.toList()
        connection.close()
        socket.listener.onClosed(socket, 1000, "closed")
        socket.fail()
        socket.listener.onMessage(socket, "late")
        scheduler.deliverCallbacks()
        assertEquals(beforeStop, statuses)
        assertTrue(socket.closed)
        assertTrue(messages.isEmpty())
        assertTrue(scheduler.delayed.isEmpty())
    }

    @Test
    fun stopDuringBackoffInvalidatesEvenAnAlreadyDequeuedRetry() {
        connection.connect()
        sockets.single().fail()
        scheduler.deliverCallbacks()
        val retry = scheduler.delayed.single()
        connection.close()
        assertTrue(scheduler.delayed.isEmpty())
        retry()
        assertEquals(1, sockets.size)
    }

    @Test
    fun reconnectAcceptsOnlyTheNewSocket() {
        connection.connect()
        val old = sockets.single()
        old.fail()
        scheduler.deliverCallbacks()
        scheduler.delayed.single().invoke()
        assertEquals(2, sockets.size)
        val new = sockets.last()
        new.opened()
        old.fail()
        old.listener.onMessage(old, "old")
        new.listener.onMessage(new, "new")
        scheduler.deliverCallbacks()
        assertEquals(listOf("new"), messages)
        assertEquals(ReconnectingWebSocket.ConnectionStatus.CONNECTED, statuses.last())
        assertTrue(scheduler.delayed.isEmpty())
    }
}
