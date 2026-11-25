package com.mefuturesaibot.app.ai

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultiTimeframeRegressionTest {

    @Test
    fun candidateReplacementPreservesFrameRotation() {
        val planner = MultiTimeframePlanner()

        planner.updateSymbols(listOf("BTCUSDT"))

        assertEquals(
            Timeframe.D1,
            planner.next(emptyMap())?.timeframe
        )

        planner.updateSymbols(listOf("ETHUSDT"))

        val next = requireNotNull(
            planner.next(emptyMap())
        )

        assertEquals("ETHUSDT", next.symbol)
        assertEquals(Timeframe.H4, next.timeframe)
    }

    @Test
    fun repeatedCandidateChangesCoverAllTimeframes() {
        val planner = MultiTimeframePlanner()
        val visited = mutableSetOf<Timeframe>()

        repeat(15) { index ->
            val symbol =
                if (index % 2 == 0) "BTCUSDT"
                else "ETHUSDT"

            planner.updateSymbols(listOf(symbol))

            val task = requireNotNull(
                planner.next(emptyMap())
            )

            assertEquals(symbol, task.symbol)
            visited.add(task.timeframe)
        }

        assertEquals(
            Timeframe.entries.toSet(),
            visited
        )
    }

    @Test
    fun changingCandidateCountPreservesFramePriority() {
        val planner = MultiTimeframePlanner()

        planner.updateSymbols(listOf("BTCUSDT"))

        planner.next(emptyMap())

        planner.updateSymbols(
            listOf("BTCUSDT", "ETHUSDT", "SOLUSDT")
        )

        val tasks = List(3) {
            requireNotNull(
                planner.next(emptyMap())
            )
        }

        assertTrue(
            tasks.all {
                it.timeframe == Timeframe.H4
            }
        )

        assertEquals(
            setOf("BTCUSDT", "ETHUSDT", "SOLUSDT"),
            tasks.map { it.symbol }.toSet()
        )
    }

    @Test
    fun closedRepositoryCannotReturnCachedHistory() {
        var now = 0L
        val requests = AtomicInteger(0)

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requests.incrementAndGet()

                val body =
                    if (
                        chain.request().url.encodedPath
                            .endsWith("/time")
                    ) {
                        """{"serverTime":120000}"""
                    } else {
                        """[[60000,"100","110","90","105","500",119999]]"""
                    }

                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("Fixture")
                    .body(body.toResponseBody())
                    .build()
            }
            .build()

        val repository = MultiTimeframeRepository(
            client = client,
            elapsed = { now }
        )

        try {
            assertEquals(
                MultiTimeframeRepository.Status.WAIT_CLOCK,
                repository.refresh(
                    "BTCUSDT",
                    Timeframe.M1
                )
            )

            now = 500L

            assertEquals(
                MultiTimeframeRepository.Status.UPDATED,
                repository.refresh(
                    "BTCUSDT",
                    Timeframe.M1
                )
            )

            assertEquals(
                MultiTimeframeRepository.Status.CACHED,
                repository.refresh(
                    "BTCUSDT",
                    Timeframe.M1
                )
            )

            repository.close()

            assertEquals(
                MultiTimeframeRepository.Status.CLOSED,
                repository.refresh(
                    "BTCUSDT",
                    Timeframe.M1
                )
            )

            assertTrue(
                repository.snapshot("BTCUSDT")
                    .isEmpty()
            )

            assertTrue(
                repository.availableFrames("BTCUSDT")
                    .isEmpty()
            )

            assertEquals(2, requests.get())
        } finally {
            repository.close()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test
    fun removedCandidateDoesNotExposeCachedHistory() {
        val cache = MultiTimeframeCache()
        val clock = AiExchangeClock()

        assertTrue(
            clock.observe(120_000L, 0L, 0L)
        )

        assertTrue(
            cache.put(
                MultiTimeframeHistory.parse(
                    "BTCUSDT",
                    Timeframe.M1,
                    """[[60000,"100","110","90","105","500",119999]]""",
                    120_000L
                )
            )
        )

        val repository = MultiTimeframeRepository(
            cache = cache,
            clock = clock,
            elapsed = { 500L }
        )

        val worker = MultiTimeframeSchedulerWorker(
            repository = repository,
            elapsed = { 500L }
        )

        try {
            worker.updateSymbols(listOf("BTCUSDT"))

            assertTrue(
                worker.snapshot("BTCUSDT")
                    .containsKey(Timeframe.M1)
            )

            worker.updateSymbols(listOf("ETHUSDT"))

            assertTrue(
                worker.snapshot("BTCUSDT")
                    .isEmpty()
            )
        } finally {
            worker.close()
        }
    }
}
