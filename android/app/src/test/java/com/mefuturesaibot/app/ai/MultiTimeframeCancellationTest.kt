package com.mefuturesaibot.app.ai

import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import okhttp3.Call

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultiTimeframeCancellationTest {

    @Test
    fun closeCancelsActiveRequestAndPreventsCacheWrite() {
        val entered = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        val active = AtomicReference<Call>()
        val now = AtomicLong(0L)

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()

                val body =
                    if (request.url.encodedPath.endsWith("/time")) {
                        """{"serverTime":120000}"""
                    } else {
                        active.set(chain.call())
                        entered.countDown()

                        if (!proceed.await(10, TimeUnit.SECONDS)) {
                            throw IOException("Fixture timeout")
                        }

                        if (chain.call().isCanceled()) {
                            throw IOException("Cancelled")
                        }

                        """[[60000,"100","110","90","105","500",119999]]"""
                    }

                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("Fixture")
                    .body(body.toResponseBody())
                    .build()
            }
            .build()

        val repository = MultiTimeframeRepository(
            client = client,
            elapsed = { now.get() }
        )

        val worker = Executors.newSingleThreadExecutor()

        try {
            assertEquals(
                MultiTimeframeRepository.Status.WAIT_CLOCK,
                repository.refresh("BTCUSDT", Timeframe.M1)
            )

            now.set(500L)

            val result = worker.submit<
                MultiTimeframeRepository.Status
            > {
                repository.refresh("BTCUSDT", Timeframe.M1)
            }

            assertTrue(
                "Request did not start",
                entered.await(5, TimeUnit.SECONDS)
            )

            repository.close()

            assertTrue(
                "Active request was not cancelled",
                requireNotNull(active.get()).isCanceled()
            )

            proceed.countDown()

            assertEquals(
                MultiTimeframeRepository.Status.CLOSED,
                result.get(5, TimeUnit.SECONDS)
            )

            assertTrue(
                repository.snapshot("BTCUSDT").isEmpty()
            )

            assertEquals(
                MultiTimeframeRepository.Status.CLOSED,
                repository.refresh("BTCUSDT", Timeframe.M1)
            )
        } finally {
            proceed.countDown()
            repository.close()
            worker.shutdownNow()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }
}
