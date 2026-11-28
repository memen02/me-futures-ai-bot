package com.mefuturesaibot.app.ai

import android.os.Looper
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ResearchDepthFeedLifecycleTest {
    @Test fun stopCancelsSnapshotAndRejectsLateDeltaOrRestartOfDisposedFeed() {
        lateinit var listener: WebSocketListener
        lateinit var socket: WebSocket
        var opens = 0
        var socketClosed = false
        val ws = object : WebSocket.Factory {
            override fun newWebSocket(request: Request, delegate: WebSocketListener): WebSocket {
                opens++
                listener = delegate
                socket = object : WebSocket {
                    override fun request() = request
                    override fun queueSize() = 0L
                    override fun send(text: String) = error("public receive-only stream")
                    override fun send(bytes: ByteString) = error("public receive-only stream")
                    override fun close(code: Int, reason: String?): Boolean { socketClosed = true; return true }
                    override fun cancel() { socketClosed = true }
                }
                return socket
            }
        }
        val executing = CountDownLatch(1)
        val release = CountDownLatch(1)
        lateinit var inFlight: Call
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            inFlight = chain.call()
            executing.countDown()
            release.await(5, TimeUnit.SECONDS)
            throw IOException("simulated disconnect")
        }.build()
        val feed = ResearchDepthFeed("BTCUSDT", ws, AiRateLimitGuard(), http)
        val executor = Executors.newSingleThreadExecutor()
        val delta = """{"s":"BTCUSDT","e":"depthUpdate","U":100,"u":101,"pu":99,"E":5000,"b":[["98","1"]],"a":[["102","1"]]}"""
        try {
            feed.start()
            listener.onMessage(socket, delta)
            shadowOf(Looper.getMainLooper()).idle()
            val snapshot = executor.submit { feed.refreshSnapshot() }
            assertTrue(executing.await(5, TimeUnit.SECONDS))
            feed.close()
            assertTrue(inFlight.isCanceled())
            assertTrue(socketClosed)
            listener.onMessage(socket, delta)
            feed.start()
            shadowOf(Looper.getMainLooper()).idle()
            release.countDown()
            snapshot.get(5, TimeUnit.SECONDS)
            assertEquals(1, opens)
            assertFalse(feed.observed())
        } finally {
            release.countDown()
            feed.close()
            executor.shutdownNow()
        }
    }
}
