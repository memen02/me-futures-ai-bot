package com.mefuturesaibot.app.ai

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultiTimeframeOptimizationTest {

    private val candleJson =
        """[[60000,"100","110","90","105","500",119999]]"""

    private fun cache(): MultiTimeframeCache {
        return MultiTimeframeCache().apply {
            assertTrue(
                put(
                    MultiTimeframeHistory.parse(
                        "BTCUSDT",
                        Timeframe.M1,
                        candleJson,
                        120_000L
                    )
                )
            )
        }
    }

    @Test
    fun availableFramesReturnsFreshData() {
        assertEquals(
            setOf(Timeframe.M1),
            cache().availableFrames(
                "BTCUSDT",
                120_000L,
                120_500L
            )
        )
    }

    @Test
    fun availableFramesRejectsExpiredData() {
        assertTrue(
            cache().availableFrames(
                "BTCUSDT",
                180_000L,
                180_500L
            ).isEmpty()
        )
    }

    @Test
    fun availableFramesRejectsUncertainBoundary() {
        assertTrue(
            cache().availableFrames(
                "BTCUSDT",
                179_999L,
                180_000L
            ).isEmpty()
        )
    }

    @Test
    fun availableFramesSeparatesSymbols() {
        assertTrue(
            cache().availableFrames(
                "ETHUSDT",
                120_000L,
                120_500L
            ).isEmpty()
        )
    }

    @Test
    fun repositoryExposesRateLimitCooldown() {
        var now = 0L
        var requests = 0

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requests++

                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(429)
                    .message("Rate limited")
                    .header("Retry-After", "120")
                    .body("{}".toResponseBody())
                    .build()
            }
            .build()

        val repository = MultiTimeframeRepository(
            client = client,
            elapsed = { now }
        )

        try {
            assertEquals(
                MultiTimeframeRepository.Status.WAIT_LIMIT,
                repository.refresh(
                    "BTCUSDT",
                    Timeframe.M1
                )
            )

            assertEquals(
                120_000L,
                repository.cooldownRemaining()
            )

            now = 1_000L

            assertEquals(
                119_000L,
                repository.cooldownRemaining()
            )

            assertEquals(
                MultiTimeframeRepository.Status.WAIT_LIMIT,
                repository.refresh(
                    "BTCUSDT",
                    Timeframe.M1
                )
            )

            assertEquals(1, requests)

            now = 120_000L

            assertEquals(
                0L,
                repository.cooldownRemaining()
            )
        } finally {
            repository.close()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test
    fun repositoryAvailabilityRequiresValidClock() {
        var now = 0L

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val body =
                    if (
                        chain.request().url.encodedPath
                            .endsWith("/time")
                    ) {
                        """{"serverTime":120000}"""
                    } else {
                        candleJson
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
            assertTrue(
                repository.availableFrames(
                    "BTCUSDT"
                ).isEmpty()
            )

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
                setOf(Timeframe.M1),
                repository.availableFrames("BTCUSDT")
            )

            now = 30_001L

            assertTrue(
                repository.availableFrames(
                    "BTCUSDT"
                ).isEmpty()
            )

            repository.close()

            assertTrue(
                repository.availableFrames(
                    "BTCUSDT"
                ).isEmpty()
            )
        } finally {
            repository.close()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }
}
