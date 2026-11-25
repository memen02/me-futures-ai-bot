package com.mefuturesaibot.app.ai

import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkerGenerationTest {

    private class ManualScheduler :
        ScheduledThreadPoolExecutor(1) {

        val delays = mutableListOf<Long>()

        private val tasks =
            ArrayDeque<Runnable>()

        override fun schedule(
            command: Runnable,
            delay: Long,
            unit: TimeUnit
        ): ScheduledFuture<*> {
            check(!isShutdown)

            delays.add(unit.toMillis(delay))
            tasks.addLast(command)

            return super.schedule(
                Runnable {},
                1,
                TimeUnit.DAYS
            )
        }

        fun runNext() {
            tasks.removeFirst().run()
        }
    }

    @Test
    fun duplicateStartSchedulesOnlyOnce() {
        val scheduler = ManualScheduler()

        val worker = MultiTimeframeSchedulerWorker(
            worker = scheduler
        )

        try {
            repeat(10) {
                worker.start()
            }

            assertEquals(
                listOf(0L),
                scheduler.delays
            )
        } finally {
            worker.close()
        }
    }

    @Test
    fun obsoleteFailureDoesNotBackoffNewCandidate() {
        val scheduler = ManualScheduler()

        lateinit var worker:
            MultiTimeframeSchedulerWorker

        val client = OkHttpClient.Builder()
            .addInterceptor {
                worker.updateSymbols(
                    listOf("ETHUSDT")
                )

                throw IOException(
                    "Old candidate request failed"
                )
            }
            .build()

        val repository = MultiTimeframeRepository(
            client = client,
            elapsed = { 0L }
        )

        worker = MultiTimeframeSchedulerWorker(
            repository = repository,
            elapsed = { 0L },
            worker = scheduler
        )

        try {
            worker.updateSymbols(
                listOf("BTCUSDT")
            )

            worker.start()
            scheduler.runNext()

            assertEquals(
                listOf(0L, 500L),
                scheduler.delays
            )

            assertTrue(worker.isRunning())
        } finally {
            worker.close()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test
    fun closedWorkerDoesNotScheduleAgain() {
        val scheduler = ManualScheduler()

        val worker = MultiTimeframeSchedulerWorker(
            worker = scheduler
        )

        try {
            worker.start()
            worker.close()

            scheduler.runNext()

            assertEquals(
                listOf(0L),
                scheduler.delays
            )

            assertFalse(worker.isRunning())
            assertTrue(scheduler.isShutdown)
        } finally {
            worker.close()
        }
    }
}
