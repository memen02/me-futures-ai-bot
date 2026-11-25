package com.mefuturesaibot.app.ai

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultiTimeframeRepositoryTest {

    private class Fixture : AutoCloseable {

        var now = 0L
        var serverTime = 120_000L
        var code = 200
        var retryAfter: String? = null
        var failNetwork = false
        var responseDelay = 0L

        var candles =
            """[[60000,"100","110","90","105","500",119999]]"""

        val requests = mutableListOf<Request>()

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                requests.add(request)

                if (failNetwork) {
                    throw IOException("Network failure")
                }

                now += responseDelay

                val body =
                    if (request.url.encodedPath.endsWith("/time")) {
                        """{"serverTime":$serverTime}"""
                    } else {
                        candles
                    }

                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("Fixture")
                    .apply {
                        retryAfter?.let {
                            header("Retry-After", it)
                        }
                    }
                    .body(
                        body.toResponseBody(
                            "application/json".toMediaType()
                        )
                    )
                    .build()
            }
            .build()

        val repository = MultiTimeframeRepository(
            client = client,
            elapsed = { now }
        )

        fun refresh() = repository.refresh(
            "BTCUSDT",
            Timeframe.M1
        )

        fun sync() {
            assertEquals(
                MultiTimeframeRepository.Status.WAIT_CLOCK,
                refresh()
            )

            now += 500L
        }

        override fun close() {
            repository.close()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test
    fun synchronizesClockBeforeCandles() {
        Fixture().use { f ->
            f.sync()

            assertEquals(1, f.requests.size)

            assertEquals(
                "/fapi/v1/time",
                f.requests.first().url.encodedPath
            )
        }
    }

    @Test
    fun loadsAndCachesClosedCandles() {
        Fixture().use { f ->
            f.sync()

            assertEquals(
                MultiTimeframeRepository.Status.UPDATED,
                f.refresh()
            )

            val history = f.repository
                .snapshot("BTCUSDT")[Timeframe.M1]

            assertNotNull(history)
            assertEquals(1, history?.candles?.size)
            assertEquals(60_000L, history?.lastOpen)
        }
    }

    @Test
    fun sendsCorrectKlineParameters() {
        Fixture().use { f ->
            f.sync()
            f.refresh()

            val url = f.requests.last().url

            assertEquals(
                "/fapi/v1/klines",
                url.encodedPath
            )

            assertEquals(
                "BTCUSDT",
                url.queryParameter("symbol")
            )

            assertEquals(
                "1m",
                url.queryParameter("interval")
            )

            assertEquals(
                "499",
                url.queryParameter("limit")
            )

            assertEquals(
                "120499",
                url.queryParameter("endTime")
            )
        }
    }

    @Test
    fun freshCacheAvoidsNetworkRequest() {
        Fixture().use { f ->
            f.sync()
            f.refresh()

            val count = f.requests.size

            assertEquals(
                MultiTimeframeRepository.Status.CACHED,
                f.refresh()
            )

            assertEquals(count, f.requests.size)
        }
    }

    @Test
    fun excludesOpenCandle() {
        Fixture().use { f ->
            f.candles = """
                [
                    [60000,"100","110","90","105","500",119999],
                    [120000,"105","115","95","110","600",179999]
                ]
            """.trimIndent()

            f.sync()

            assertEquals(
                MultiTimeframeRepository.Status.UPDATED,
                f.refresh()
            )

            val history = f.repository
                .snapshot("BTCUSDT")[Timeframe.M1]

            assertEquals(1, history?.candles?.size)
            assertEquals(60_000L, history?.lastOpen)
        }
    }

    @Test
    fun rejectsOpenOnlyHistory() {
        Fixture().use { f ->
            f.candles =
                """[[120000,"105","115","95","110","600",179999]]"""

            f.sync()

            assertEquals(
                MultiTimeframeRepository.Status.WAIT_DATA,
                f.refresh()
            )

            assertTrue(
                f.repository.snapshot("BTCUSDT").isEmpty()
            )
        }
    }

    @Test
    fun rejectsMalformedResponse() {
        Fixture().use { f ->
            f.sync()
            f.candles = "invalid json"

            assertEquals(
                MultiTimeframeRepository.Status.FAILED,
                f.refresh()
            )

            assertTrue(
                f.repository.snapshot("BTCUSDT").isEmpty()
            )
        }
    }

    @Test
    fun rejectsCandleBoundaryCrossedDuringRequest() {
        Fixture().use { f ->
            f.serverTime = 179_000L

            f.sync()
            f.responseDelay = 600L

            assertEquals(
                MultiTimeframeRepository.Status.WAIT_DATA,
                f.refresh()
            )

            assertTrue(
                f.repository.snapshot("BTCUSDT").isEmpty()
            )
        }
    }

    @Test
    fun rejectsUncertainCandleBoundary() {
        Fixture().use { f ->
            f.serverTime = 179_000L
            f.responseDelay = 600L

            f.sync()
            f.responseDelay = 0L

            assertEquals(
                MultiTimeframeRepository.Status.WAIT_DATA,
                f.refresh()
            )
        }
    }

    @Test
    fun expiredClockHidesCachedData() {
        Fixture().use { f ->
            f.sync()
            f.refresh()

            f.now = 30_001L

            assertTrue(
                f.repository.snapshot("BTCUSDT").isEmpty()
            )
        }
    }

    @Test
    fun rateLimitPreventsAdditionalRequests() {
        Fixture().use { f ->
            f.sync()
            f.code = 429
            f.retryAfter = "120"

            assertEquals(
                MultiTimeframeRepository.Status.WAIT_LIMIT,
                f.refresh()
            )

            val count = f.requests.size

            f.now += 1000L

            assertEquals(
                MultiTimeframeRepository.Status.WAIT_LIMIT,
                f.refresh()
            )

            assertEquals(count, f.requests.size)
        }
    }

    @Test
    fun networkFailureReleasesRequestGuard() {
        Fixture().use { f ->
            f.sync()
            f.failNetwork = true

            assertEquals(
                MultiTimeframeRepository.Status.FAILED,
                f.refresh()
            )

            f.failNetwork = false
            f.now += 500L

            assertEquals(
                MultiTimeframeRepository.Status.UPDATED,
                f.refresh()
            )
        }
    }

    @Test
    fun oversizedResponseIsRejected() {
        Fixture().use { f ->
            f.sync()
            f.candles = "x".repeat(524_289)

            assertEquals(
                MultiTimeframeRepository.Status.FAILED,
                f.refresh()
            )

            assertTrue(
                f.repository.snapshot("BTCUSDT").isEmpty()
            )
        }
    }

    @Test
    fun closedRepositoryRejectsNewRequests() {
        Fixture().use { f ->
            f.repository.close()

            assertEquals(
                MultiTimeframeRepository.Status.CLOSED,
                f.refresh()
            )

            assertTrue(f.requests.isEmpty())
        }
    }

    @Test
    fun closeClearsCachedHistory() {
        Fixture().use { f ->
            f.sync()
            f.refresh()

            assertFalse(
                f.repository.snapshot("BTCUSDT").isEmpty()
            )

            f.repository.close()

            assertTrue(
                f.repository.snapshot("BTCUSDT").isEmpty()
            )
        }
    }
}
