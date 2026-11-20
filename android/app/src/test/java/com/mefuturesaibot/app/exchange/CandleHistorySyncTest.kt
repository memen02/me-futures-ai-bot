package com.mefuturesaibot.app.exchange

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

private class CandleScheduler : Scheduler {
    val callbacks = LinkedBlockingQueue<() -> Unit>()
    val delayed = mutableListOf<Pair<Long, () -> Unit>>()
    override fun post(action: () -> Unit) { callbacks.add(action) }
    override fun postDelayed(delayMs: Long, action: () -> Unit) { delayed.add(delayMs to action) }
    override fun cancelAll() {
        callbacks.clear()
        delayed.clear()
    }
    fun nextCallback(): () -> Unit = requireNotNull(callbacks.poll(5, TimeUnit.SECONDS))
}

class CandleHistorySyncTest {
    private val scheduler = CandleScheduler()
    private var minute = 0
    private var responseCode = 200
    private var omitMinute: Int? = null
    private var now = 1000L
    private val requests = LinkedBlockingQueue<Request>()
    private val updates = mutableListOf<CandleHistory>()
    private fun row(i: Int): String {
        val start = i * 60_000L
        return """[$start,"10.00","12.5","9.9","11.1","0.5",${start + 59_999}]"""
    }
    private fun rows(start: Int) = (start..start + CandleHistory.SIZE).filter { it != omitMinute }.map(::row)

    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        requests.add(chain.request())
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(responseCode).message("fixture").header("Retry-After", "180")
            .body(rows(minute).joinToString(prefix = "[", postfix = "]").toResponseBody()).build()
    }.build()
    private val sync = CandleHistorySync(client, scheduler, { now }) { updates.add(it) }

    @After fun tearDown() {
        sync.stop()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    @Test fun startupAndReconnectRecoverAllMissingMinutesInWindow() {
        sync.start()
        scheduler.nextCallback().invoke()
        val request = requests.remove()
        assertEquals("GET", request.method)
        assertEquals("https://fapi.binance.com/fapi/v1/klines?symbol=BTCUSDT&interval=1m&limit=121", request.url.toString())
        assertEquals(60_000L, scheduler.delayed.single().first)
        assertEquals(119 * 60_000L, updates.last().candles.last().openTime)
        minute = 3
        sync.refresh()
        scheduler.nextCallback().invoke()
        assertEquals((3..122).map { it * 60_000L }, updates.last().candles.map { it.openTime })
        assertEquals("10.00", updates.last().candles.last().open.toPlainString())
    }

    @Test fun incompleteOrOlderResponsePreservesLastGoodWindow() {
        minute = 2
        sync.start()
        scheduler.nextCallback().invoke()
        val good = updates.last().candles
        minute = 3
        omitMinute = 40
        sync.refresh()
        scheduler.nextCallback().invoke()
        assertEquals(good, updates.last().candles)
        assertTrue(updates.last().status.contains("tidak lengkap"))
        omitMinute = null
        minute = 0
        sync.refresh()
        scheduler.nextCallback().invoke()
        assertEquals(good, updates.last().candles)
    }

    @Test fun longOutageRestoresLatestTwoHoursAndDiscardsFormingCandle() {
        sync.start()
        scheduler.nextCallback().invoke()
        minute = 500
        scheduler.delayed.single().second()
        scheduler.nextCallback().invoke()
        assertEquals((500..619).map { it * 60_000L }, updates.last().candles.map { it.openTime })
    }

    @Test fun repeatedAndUnorderedRowsHaveOneChronologicalEntryPerMinute() {
        val payload = (rows(5).reversed() + row(40)).joinToString(prefix = "[", postfix = "]")
        val actual = CandleHistory.parseClosedCandles(payload)
        assertEquals((5..124).map { it * 60_000L }, actual.map { it.openTime })
    }

    @Test fun stopIgnoresLateResponseAndCancelsRefresh() {
        sync.start()
        val callback = scheduler.nextCallback()
        sync.stop()
        callback()
        assertTrue(updates.single().candles.isEmpty())
        assertTrue(scheduler.delayed.isEmpty())
    }

    @Test fun rateLimitSurvivesReconnectAndRecoversAfterRetryAfter() {
        responseCode = 429
        sync.start()
        scheduler.nextCallback().invoke()
        assertTrue(updates.last().status.contains("429"))
        sync.refresh()
        assertEquals(1, requests.size)
        assertEquals(180_000L, scheduler.delayed.single().first)
        now += 180_000L
        responseCode = 200
        scheduler.delayed.single().second()
        scheduler.nextCallback().invoke()
        assertEquals(120, updates.last().candles.size)
    }
}
