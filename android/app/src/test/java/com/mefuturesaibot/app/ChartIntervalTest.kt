package com.mefuturesaibot.app

import com.mefuturesaibot.app.exchange.CandleScheduler
import com.mefuturesaibot.app.exchange.PublicChartFeed
import com.mefuturesaibot.app.exchange.chartIntervals
import java.util.concurrent.LinkedBlockingQueue
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class ChartIntervalTest {
    @Test fun nativeIntervalsIgnoreLateRepliesAndRetainSameIntervalOnFailure() {
        val scheduler = CandleScheduler()
        val requests = LinkedBlockingQueue<String>()
        val january = 1704067200000L
        val february = 1706745600000L
        val march = 1709251200000L
        val body = """[[$january,"10","13","9","12","4",${february - 1}],[$february,"12","13","11","12","8",${march - 1}]]"""
        var code = 200
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests.add(chain.request().url.queryParameter("interval")!!)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
                .header("Retry-After", "60").body(body.toResponseBody()).build()
        }.build()
        var elapsed = 0L
        val feed = PublicChartFeed(client, scheduler, { elapsed }) {}
        try {
            assertEquals(16, chartIntervals.distinct().size)
            feed.setVisible(true)
            val old = scheduler.nextCallback()
            assertEquals("1m", requests.remove())
            feed.select("1M")
            old()
            assertTrue(feed.state.bars.isEmpty())
            scheduler.nextCallback().invoke()
            assertEquals("1M", requests.remove())
            assertEquals(listOf(january, february), feed.state.bars.map { it.openTime })
            code = 429
            scheduler.delayed.removeAt(0).second()
            scheduler.nextCallback().invoke()
            assertEquals("1M", requests.remove())
            assertFalse(feed.state.fresh)
            assertEquals(2, feed.state.bars.size)
            assertTrue(feed.state.status.contains("429"))
            feed.select("1s")
            assertTrue(feed.state.bars.isEmpty())
            assertEquals(60_000L, scheduler.delayed.single().first)
            assertTrue(requests.isEmpty())
            elapsed = 60_000
            scheduler.delayed.removeAt(0).second()
            val late = scheduler.nextCallback()
            assertEquals("1s", requests.remove())
            feed.setVisible(false)
            late()
            assertTrue(scheduler.delayed.isEmpty())
        } finally {
            feed.setVisible(false)
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
