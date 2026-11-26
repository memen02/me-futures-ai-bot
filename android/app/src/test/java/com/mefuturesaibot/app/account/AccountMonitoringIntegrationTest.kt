package com.mefuturesaibot.app.account

import com.mefuturesaibot.app.demo.FuturesVenue
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class AccountMonitoringIntegrationTest {
    @Test fun balanceRequiresConnectedSessionAndFreshRead() {
        val venue = FuturesVenue.DEMO
        val revisions = CredentialRevisionGuard()
        val schedulers = mutableListOf<BalanceTestScheduler>()
        val workers = mutableListOf<BalanceTestWorker>()
        val sockets = mutableListOf<FakeSocket>()
        val clock = java.util.concurrent.atomic.AtomicLong(0L)
        val dependencies = AccountMonitoringDependencies(
            balanceFactory = { target, _ ->
                AccountBalanceFactory {
                    FakeBalanceAccess(1, Thread.currentThread()) {
                        AccountBalance(target, BigDecimal("100"), BigDecimal("105"),
                            BigDecimal("80"), BigDecimal("5"), clock.get())
                    }
                }
            },
            keyFactory = { _, _ ->
                AccountSessionKeyFactory {
                    object : AccountSessionKeyAccess {
                        override fun start() = "fake-key"
                        override fun keepAlive(expectedKey: String) = expectedKey
                        override fun close() = Unit
                    }
                }
            },
            socketFactory = { _, _ ->
                AccountSessionSocketFactory { _, status, event, _ ->
                    FakeSocket(status, event).also { sockets.add(it) }
                }
            },
            schedulerFactory = { BalanceTestScheduler().also {
                it.moveClockTo(clock.get())
                schedulers.add(it)
            } },
            workerFactory = { BalanceTestWorker().also { workers.add(it) } }
        )
        val guard = AccountRateLimitGuard(elapsed = { clock.get() }, wallClock = { 0L })
        val coordinator = AccountMonitoringCoordinator(
            currentRevision = { revisions.current(it.name) },
            scope = AccountMonitoringRateLimitScope(guard),
            elapsed = { clock.get() },
            dependencies = dependencies
        )
        fun credentials() = CredentialRevisionSnapshot(
            BinanceCredentials("fake-key", "fake-secret"), revisions.current(venue.name))
        try {
            assertTrue(coordinator.start(venue, credentials()))
            schedulers[0].runDue()
            workers[0].awaitIdle()
            schedulers[0].drainReady()
            assertNull(coordinator.snapshot()?.balance)
            workers[1].awaitIdle()
            schedulers[1].drainReady()
            val oldSocket = sockets.single()
            oldSocket.open()
            assertNull(coordinator.snapshot()?.balance)
            clock.set(3000L)
            schedulers.forEach { it.moveClockTo(clock.get()) }
            schedulers[0].runDue()
            workers[0].awaitIdle()
            schedulers[0].drainReady()
            assertEquals(BigDecimal("100"), coordinator.snapshot()?.balance?.wallet)
            oldSocket.changed()
            assertNull(coordinator.snapshot()?.balance)
            coordinator.stop()
            assertTrue(oldSocket.closed)
            assertNull(coordinator.snapshot())
            assertTrue(coordinator.start(venue, credentials()))
            oldSocket.open()
            oldSocket.changed()
            assertNull(coordinator.snapshot()?.balance)
            revisions.invalidate(venue.name)
            assertNull(coordinator.snapshot())
            assertFalse(coordinator.refresh())
        } finally {
            coordinator.close()
            workers.forEach {
                it.shutdownNow()
                assertTrue(it.awaitTermination(5L, java.util.concurrent.TimeUnit.SECONDS))
            }
        }
    }

    private class FakeSocket(
        private val status: (AccountSocketStatus) -> Unit,
        private val event: (AccountStreamEvent) -> Unit
    ) : AccountSessionSocket {
        var closed = false
        override fun connect() = status(AccountSocketStatus.CONNECTING)
        fun open() = status(AccountSocketStatus.CONNECTED)
        fun changed() = event(AccountStreamEvent(AccountStreamEventType.ACCOUNT_CHANGED, 1L))
        override fun close() { closed = true }
    }
}
