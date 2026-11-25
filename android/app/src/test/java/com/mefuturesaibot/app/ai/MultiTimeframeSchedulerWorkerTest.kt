package com.mefuturesaibot.app.ai

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultiTimeframeSchedulerWorkerTest {

    private class Fixture : AutoCloseable {

        val requests = AtomicInteger(0)
        val entered = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val active = AtomicReference<Call>()

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requests.incrementAndGet()
                active.set(chain.call())
                entered.countDown()

                try {
                    if (
                        !proceed.await(
                            10,
                            TimeUnit.SECONDS
                        )
                    ) {
                        throw IOException(
                            "Fixture timeout"
                        )
                    }

                    if (chain.call().isCanceled()) {
                        throw IOException("Cancelled")
                    }

                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("Fixture")
                        .body(
                            """{"serverTime":120000}"""
                                .toResponseBody()
                        )
                        .build()
                } finally {
                    finished.countDown()
                }
            }
            .build()

        val repository = MultiTimeframeRepository(
            client = client,
            elapsed = { 0L }
        )

        val worker = MultiTimeframeSchedulerWorker(
            repository = repository,
            elapsed = { 0L }
        )

        override fun close() {
            worker.close()
            proceed.countDown()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test
    fun workerInitiallyStopped() {
        Fixture().use { f ->
            assertFalse(f.worker.isRunning())
            assertEquals(0, f.requests.get())
        }
    }

    @Test
    fun workerStartsWithoutCandidates() {
        Fixture().use { f ->
            f.worker.start()

            assertTrue(f.worker.isRunning())

            assertFalse(
                f.entered.await(
                    300,
                    TimeUnit.MILLISECONDS
                )
            )

            assertEquals(0, f.requests.get())
        }
    }

    @Test
    fun workerRequestsDataForActiveCandidate() {
        Fixture().use { f ->
            f.worker.updateSymbols(
                listOf("BTCUSDT")
            )

            f.worker.start()

            assertTrue(
                f.entered.await(
                    5,
                    TimeUnit.SECONDS
                )
            )

            assertEquals(1, f.requests.get())
            assertTrue(f.worker.isRunning())
        }
    }

    @Test
    fun duplicateStartDoesNotCreateSecondRequest() {
        Fixture().use { f ->
            f.worker.updateSymbols(
                listOf("BTCUSDT")
            )

            repeat(10) {
                f.worker.start()
            }

            assertTrue(
                f.entered.await(
                    5,
                    TimeUnit.SECONDS
                )
            )

            repeat(10) {
                f.worker.start()
            }

            assertEquals(1, f.requests.get())
        }
    }

    @Test
    fun closeCancelsActiveRequest() {
        Fixture().use { f ->
            f.worker.updateSymbols(
                listOf("BTCUSDT")
            )

            f.worker.start()

            assertTrue(
                f.entered.await(
                    5,
                    TimeUnit.SECONDS
                )
            )

            f.worker.close()

            assertTrue(
                requireNotNull(f.active.get())
                    .isCanceled()
            )

            f.proceed.countDown()

            assertTrue(
                f.finished.await(
                    5,
                    TimeUnit.SECONDS
                )
            )

            assertFalse(f.worker.isRunning())

            assertTrue(
                f.worker.snapshot(
                    "BTCUSDT"
                ).isEmpty()
            )
        }
    }

    @Test
    fun closedWorkerCannotRestart() {
        Fixture().use { f ->
            f.worker.close()

            f.worker.updateSymbols(
                listOf("BTCUSDT")
            )

            f.worker.start()

            assertFalse(f.worker.isRunning())
            assertEquals(0, f.requests.get())
        }
    }

    @Test
    fun repeatedCloseIsSafe() {
        Fixture().use { f ->
            repeat(10) {
                f.worker.close()
            }

            assertFalse(f.worker.isRunning())
        }
    }

    @Test
    fun inactiveCandidateSnapshotIsEmpty() {
        Fixture().use { f ->
            f.worker.updateSymbols(
                listOf("BTCUSDT")
            )

            assertTrue(
                f.worker.snapshot(
                    "ETHUSDT"
                ).isEmpty()
            )
        }
    }
}
