package com.mefuturesaibot.app.account

import com.mefuturesaibot.app.exchange.Scheduler
import java.util.ArrayDeque
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/**
 * Deterministic session lifecycle tests.
 *
 * No Android clock, network, credentials,
 * real exchange or trading operations.
 */
class AccountSessionManagerTest {

    private class TestWorker :
        AbstractExecutorService() {

        private val tasks = ArrayDeque<Runnable>()

        private var stopped = false

        val queued: Int
            get() = tasks.size

        override fun execute(command: Runnable) {
            if (stopped) {
                throw RejectedExecutionException()
            }

            tasks.addLast(command)
        }

        fun runNext() {
            check(tasks.isNotEmpty())

            tasks.removeFirst().run()
        }

        fun drain() {
            var count = 0

            while (tasks.isNotEmpty()) {
                check(++count <= 1000)

                runNext()
            }
        }

        override fun shutdown() {
            stopped = true
        }

        override fun shutdownNow(): MutableList<Runnable> {
            stopped = true

            val remaining = tasks.toMutableList()

            tasks.clear()

            return remaining
        }

        override fun isShutdown(): Boolean =
            stopped

        override fun isTerminated(): Boolean =
            stopped && tasks.isEmpty()

        override fun awaitTermination(
            timeout: Long,
            unit: TimeUnit
        ): Boolean = isTerminated()
    }

    private class TestScheduler : Scheduler {

        data class Scheduled(
            val due: Long,
            val action: () -> Unit
        )

        var now = 0L

        val ready = ArrayDeque<() -> Unit>()

        val delayed = mutableListOf<Scheduled>()

        override fun post(action: () -> Unit) {
            ready.addLast(action)
        }

        override fun postDelayed(
            delayMs: Long,
            action: () -> Unit
        ) {
            require(delayMs >= 0L)

            val due = Math.addExact(now, delayMs)

            delayed.add(
                Scheduled(due, action)
            )
        }

        override fun cancelAll() {
            ready.clear()
            delayed.clear()
        }

        fun drain() {
            var count = 0

            while (ready.isNotEmpty()) {
                check(++count <= 1000)

                ready.removeFirst().invoke()
            }
        }

        fun advanceBy(delta: Long) {
            require(delta >= 0L)

            val target = Math.addExact(now, delta)

            var count = 0

            drain()

            while (true) {
                val next =
                    delayed.minByOrNull { it.due }
                        ?: break

                if (next.due > target) {
                    break
                }

                check(++count <= 10000)

                delayed.remove(next)

                now = maxOf(now, next.due)

                next.action()

                drain()
            }

            now = target

            drain()
        }
    }

    private class FakeKeyAccess :
        AccountSessionKeyAccess {

        var startCalls = 0

        var keepAliveCalls = 0

        var closeCalls = 0

        var startAction: () -> String = {
            "fake-key"
        }

        var keepAliveAction: (String) -> String = {
            it
        }

        override fun start(): String {
            check(closeCalls == 0)

            startCalls++

            return startAction()
        }

        override fun keepAlive(
            expectedKey: String
        ): String {
            check(closeCalls == 0)

            keepAliveCalls++

            return keepAliveAction(expectedKey)
        }

        override fun close() {
            closeCalls++
        }
    }

    private class FakeSocket(
        val key: String,
        private val onStatus:
            (AccountSocketStatus) -> Unit,
        private val onEvent:
            (AccountStreamEvent) -> Unit,
        private val onFailure:
            (AccountSocketFailure) -> Unit
    ) : AccountSessionSocket {

        var connectCalls = 0

        var closeCalls = 0

        override fun connect() {
            connectCalls++

            onStatus(
                AccountSocketStatus.CONNECTING
            )
        }

        fun open() {
            onStatus(
                AccountSocketStatus.CONNECTED
            )
        }

        fun event(event: AccountStreamEvent) {
            onEvent(event)
        }

        fun expire() {
            onStatus(
                AccountSocketStatus.SESSION_EXPIRED
            )
        }

        fun fail(
            failure: AccountSocketFailure =
                AccountSocketFailure()
        ) {
            onStatus(
                AccountSocketStatus.DISCONNECTED
            )

            onFailure(failure)
        }

        override fun close() {
            closeCalls++
        }
    }

    private val scheduler = TestScheduler()

    private val worker = TestWorker()

    private val guard = AccountRateLimitGuard(
        elapsed = { scheduler.now }
    )

    private val keys =
        mutableListOf<FakeKeyAccess>()

    private val sockets =
        mutableListOf<FakeSocket>()

    private val states =
        mutableListOf<AccountSessionSnapshot>()

    private val events =
        mutableListOf<AccountStreamEvent>()

    private var stateObserver:
        (AccountSessionSnapshot) -> Unit = {}

    private var eventObserver:
        (AccountStreamEvent) -> Unit = {}

    private var configureKey:
        (FakeKeyAccess) -> Unit = {}

    private var configureSocket:
        (FakeSocket) -> Unit = {}

    private val manager = AccountSessionManager(
        keyFactory = AccountSessionKeyFactory {
            FakeKeyAccess().also {
                configureKey(it)

                keys.add(it)
            }
        },
        socketFactory = AccountSessionSocketFactory {
            key,
            onStatus,
            onEvent,
            onFailure ->

            FakeSocket(
                key,
                onStatus,
                onEvent,
                onFailure
            ).also {
                configureSocket(it)

                sockets.add(it)
            }
        },
        scheduler = scheduler,
        rateLimitGuard = guard,
        onState = {
            states.add(it)

            stateObserver(it)
        },
        onEvent = {
            events.add(it)

            eventObserver(it)
        },
        worker = worker,
        elapsed = { scheduler.now },
        jitterPercent = { 0 }
    )

    private val accountEvent = AccountStreamEvent(
        AccountStreamEventType.ACCOUNT_CHANGED,
        1700000000000L
    )

    private fun assertState(
        expected: AccountSessionState
    ) {
        assertEquals(
            expected,
            manager.snapshot().state
        )
    }

    private fun completeRest() {
        worker.drain()

        scheduler.drain()
    }

    private fun connect(): FakeSocket {
        manager.start()

        completeRest()

        assertState(
            AccountSessionState.CONNECTING
        )

        return sockets.last().also {
            it.open()

            assertState(
                AccountSessionState.CONNECTED
            )
        }
    }

    @After
    fun cleanup() {
        manager.close()
    }

    @Test
    fun initialStateIsStopped() {
        assertState(
            AccountSessionState.STOPPED
        )

        assertEquals(
            0L,
            manager.snapshot().generation
        )

        assertTrue(keys.isEmpty())
        assertTrue(sockets.isEmpty())
        assertTrue(states.isEmpty())
        assertEquals(0, worker.queued)
    }

    @Test
    fun startDefersBlockingRestToWorker() {
        manager.start()

        assertState(
            AccountSessionState.ACQUIRING_KEY
        )

        assertEquals(1, keys.size)
        assertEquals(0, keys.single().startCalls)
        assertEquals(1, worker.queued)
        assertTrue(sockets.isEmpty())
    }

    @Test
    fun restResultWaitsForController() {
        manager.start()

        worker.runNext()

        assertEquals(1, keys.single().startCalls)

        assertState(
            AccountSessionState.ACQUIRING_KEY
        )

        assertTrue(sockets.isEmpty())
        assertEquals(1, scheduler.ready.size)

        scheduler.drain()

        assertState(
            AccountSessionState.CONNECTING
        )

        assertEquals(1, sockets.size)
        assertEquals(1, sockets.single().connectCalls)
    }

    @Test
    fun successfulConnectionPublishesExpectedStates() {
        connect()

        assertEquals(
            listOf(
                AccountSessionState.ACQUIRING_KEY,
                AccountSessionState.CONNECTING,
                AccountSessionState.CONNECTED
            ),
            states.map { it.state }
        )

        assertTrue(
            states.all { it.generation == 1L }
        )

        assertEquals(
            "fake-key",
            sockets.single().key
        )

        assertEquals(
            1,
            scheduler.delayed.size
        )
    }

    @Test
    fun duplicateStartDoesNotCreateResources() {
        manager.start()
        manager.start()

        assertEquals(1, keys.size)
        assertEquals(1, worker.queued)

        completeRest()

        manager.start()

        sockets.single().open()

        manager.start()

        assertEquals(1, keys.size)
        assertEquals(1, sockets.size)
        assertEquals(1, sockets.single().connectCalls)
    }

    @Test
    fun duplicateConnectedStatusDoesNotDuplicateMaintenance() {
        val socket = connect()

        socket.open()
        socket.open()

        assertEquals(
            1,
            states.count {
                it.state == AccountSessionState.CONNECTED
            }
        )

        assertEquals(
            1,
            scheduler.delayed.size
        )
    }

    @Test
    fun accountEventsRequireConnectedState() {
        manager.start()

        completeRest()

        val socket = sockets.single()

        socket.event(accountEvent)

        assertTrue(events.isEmpty())

        socket.open()

        socket.event(accountEvent)

        assertEquals(
            listOf(accountEvent),
            events
        )
    }

    @Test
    fun ignoresInvalidAndUnknownEvents() {
        val socket = connect()

        socket.event(
            AccountStreamEvent(
                AccountStreamEventType.INVALID
            )
        )

        socket.event(
            AccountStreamEvent(
                AccountStreamEventType.IGNORED
            )
        )

        assertTrue(events.isEmpty())

        assertState(
            AccountSessionState.CONNECTED
        )
    }

    @Test
    fun stopBeforeWorkerPreventsRestRequest() {
        manager.start()

        val access = keys.single()

        manager.stop()

        completeRest()

        assertState(
            AccountSessionState.STOPPED
        )

        assertEquals(0, access.startCalls)
        assertEquals(1, access.closeCalls)
        assertTrue(sockets.isEmpty())
        assertTrue(scheduler.delayed.isEmpty())
        assertFalse(worker.isShutdown)
    }

    @Test
    fun stopDiscardsAlreadyPostedRestResult() {
        manager.start()

        worker.runNext()

        val stale = scheduler.ready.single()

        manager.stop()

        stale.invoke()

        assertState(
            AccountSessionState.STOPPED
        )

        assertTrue(sockets.isEmpty())
        assertEquals(1, keys.single().closeCalls)
    }

    @Test
    fun stopClosesConnectedResourcesOnce() {
        val socket = connect()

        manager.stop()
        manager.stop()

        assertState(
            AccountSessionState.STOPPED
        )

        assertEquals(1, socket.closeCalls)
        assertEquals(1, keys.single().closeCalls)
        assertTrue(scheduler.ready.isEmpty())
        assertTrue(scheduler.delayed.isEmpty())
        assertFalse(worker.isShutdown)
    }

    @Test
    fun restartUsesFreshGenerationAndResources() {
        val oldSocket = connect()

        val oldGeneration =
            manager.snapshot().generation

        manager.stop()

        val newSocket = connect()

        assertEquals(
            oldGeneration + 1L,
            manager.snapshot().generation
        )

        assertEquals(2, keys.size)
        assertEquals(2, sockets.size)

        assertNotSame(oldSocket, newSocket)

        assertEquals(1, oldSocket.closeCalls)
        assertEquals(0, newSocket.closeCalls)
    }

    @Test
    fun oldSocketCallbacksCannotChangeNewSession() {
        val oldSocket = connect()

        manager.stop()

        val currentSocket = connect()

        val current = manager.snapshot()

        oldSocket.open()
        oldSocket.event(accountEvent)
        oldSocket.expire()
        oldSocket.fail()

        assertEquals(
            current,
            manager.snapshot()
        )

        assertTrue(events.isEmpty())
        assertEquals(0, currentSocket.closeCalls)

        currentSocket.event(accountEvent)

        assertEquals(1, events.size)
    }

    @Test
    fun closeIsPermanentAndShutsDownWorker() {
        val socket = connect()

        manager.close()
        manager.close()
        manager.start()
        manager.stop()

        assertState(
            AccountSessionState.CLOSED
        )

        assertTrue(worker.isShutdown)
        assertEquals(1, keys.size)
        assertEquals(1, socket.closeCalls)
        assertEquals(1, keys.single().closeCalls)
        assertTrue(scheduler.delayed.isEmpty())
    }

    @Test
    fun throwingStateObserverDoesNotBreakLifecycle() {
        stateObserver = {
            throw IllegalStateException(
                "fake observer failure"
            )
        }

        connect()

        manager.stop()

        assertState(
            AccountSessionState.STOPPED
        )

        assertEquals(1, sockets.single().closeCalls)
        assertEquals(1, keys.single().closeCalls)
    }

    @Test
    fun throwingEventObserverDoesNotBreakSession() {
        val socket = connect()

        eventObserver = {
            throw IllegalStateException(
                "fake event observer failure"
            )
        }

        socket.event(accountEvent)
        socket.event(accountEvent)

        assertEquals(2, events.size)

        assertState(
            AccountSessionState.CONNECTED
        )

        assertEquals(0, socket.closeCalls)
    }

    @Test
    fun observerCanStopBeforeKeyFactoryRuns() {
        stateObserver = {
            if (
                it.state ==
                    AccountSessionState.ACQUIRING_KEY
            ) {
                manager.stop()
            }
        }

        manager.start()

        assertState(
            AccountSessionState.STOPPED
        )

        assertTrue(keys.isEmpty())
        assertTrue(sockets.isEmpty())
        assertEquals(0, worker.queued)
    }

    @Test
    fun observerCanStopBeforeSocketFactoryRuns() {
        stateObserver = {
            if (
                it.state ==
                    AccountSessionState.CONNECTING
            ) {
                manager.stop()
            }
        }

        manager.start()

        completeRest()

        assertState(
            AccountSessionState.STOPPED
        )

        assertTrue(sockets.isEmpty())
        assertEquals(1, keys.single().closeCalls)
    }

    @Test
    fun observerCanCloseWhenConnected() {
        stateObserver = {
            if (
                it.state ==
                    AccountSessionState.CONNECTED
            ) {
                manager.close()
            }
        }

        manager.start()

        completeRest()

        sockets.single().open()

        assertState(
            AccountSessionState.CLOSED
        )

        assertTrue(worker.isShutdown)
        assertTrue(scheduler.delayed.isEmpty())
        assertEquals(1, sockets.single().closeCalls)
    }

    @Test
    fun rejectedWorkerSubmissionReleasesKeyClient() {
        worker.shutdown()

        manager.start()

        assertState(
            AccountSessionState.RETRY_WAIT
        )

        assertEquals(1, keys.single().closeCalls)
        assertEquals(0, keys.single().startCalls)
        assertTrue(sockets.isEmpty())
        assertEquals(1, scheduler.delayed.size)
    }

    @Test
    fun keyAcquisitionFailureSchedulesRetry() {
        configureKey = {
            it.startAction = {
                throw IllegalStateException(
                    "fake network failure"
                )
            }
        }

        manager.start()
        completeRest()

        assertState(AccountSessionState.RETRY_WAIT)

        assertEquals(1, keys.single().startCalls)
        assertEquals(1, keys.single().closeCalls)
        assertTrue(sockets.isEmpty())

        assertEquals(
            2000L,
            scheduler.delayed.single().due
        )
    }

    @Test
    fun retryCreatesFreshKeyClientAndGeneration() {
        configureKey = {
            it.startAction = {
                throw IllegalStateException()
            }
        }

        manager.start()
        completeRest()

        val oldGeneration =
            manager.snapshot().generation

        configureKey = {}

        scheduler.advanceBy(2000L)

        assertState(
            AccountSessionState.ACQUIRING_KEY
        )

        assertEquals(
            oldGeneration + 1L,
            manager.snapshot().generation
        )

        completeRest()

        assertState(AccountSessionState.CONNECTING)

        assertEquals(2, keys.size)
        assertEquals(1, sockets.size)

        sockets.single().open()

        assertState(AccountSessionState.CONNECTED)
    }

    @Test
    fun socketFailureReleasesResourcesBeforeRetry() {
        val socket = connect()

        socket.fail()

        assertState(AccountSessionState.RETRY_WAIT)

        assertEquals(1, socket.closeCalls)
        assertEquals(1, keys.single().closeCalls)

        assertEquals(
            2000L,
            scheduler.delayed.single().due
        )

        assertEquals(0, worker.queued)
    }

    @Test
    fun reconnectDoesNotRunBeforeDeadline() {
        connect().fail()

        scheduler.advanceBy(1999L)

        assertState(AccountSessionState.RETRY_WAIT)

        assertEquals(1, keys.size)
        assertEquals(0, worker.queued)

        scheduler.advanceBy(1L)

        assertState(
            AccountSessionState.ACQUIRING_KEY
        )

        assertEquals(2, keys.size)
        assertEquals(1, worker.queued)
    }

    @Test
    fun repeatedFailuresUseBoundedExponentialBackoff() {
        configureKey = {
            it.startAction = {
                throw IllegalStateException()
            }
        }

        manager.start()
        completeRest()

        val expectedDelays = listOf(
            2000L,
            4000L,
            8000L,
            16000L,
            32000L,
            60000L,
            60000L
        )

        expectedDelays.forEach { delay ->
            assertState(
                AccountSessionState.RETRY_WAIT
            )

            assertEquals(
                delay,
                scheduler.delayed.single().due -
                    scheduler.now
            )

            scheduler.advanceBy(delay)

            completeRest()
        }

        assertEquals(
            expectedDelays.size + 1,
            keys.size
        )

        assertTrue(sockets.isEmpty())
    }

    @Test
    fun briefConnectionDoesNotResetBackoff() {
        connect().fail()

        scheduler.advanceBy(2000L)

        completeRest()

        sockets.last().open()

        assertState(AccountSessionState.CONNECTED)

        sockets.last().fail()

        assertEquals(
            4000L,
            scheduler.delayed.single().due -
                scheduler.now
        )
    }

    @Test
    fun stableConnectionResetsBackoff() {
        connect().fail()

        scheduler.advanceBy(2000L)

        completeRest()

        sockets.last().open()

        scheduler.advanceBy(60000L)

        sockets.last().fail()

        assertEquals(
            2000L,
            scheduler.delayed.single().due -
                scheduler.now
        )
    }

    @Test
    fun sessionExpirationRequiresFreshConnection() {
        val socket = connect()

        socket.expire()

        assertState(AccountSessionState.RETRY_WAIT)

        assertEquals(1, socket.closeCalls)

        scheduler.advanceBy(2000L)

        completeRest()

        assertEquals(2, keys.size)
        assertEquals(2, sockets.size)

        assertNotSame(socket, sockets.last())
    }

    @Test
    fun duplicateFailuresDoNotDuplicateRetry() {
        val socket = connect()

        repeat(10) {
            socket.fail()
        }

        assertState(AccountSessionState.RETRY_WAIT)

        assertEquals(1, socket.closeCalls)
        assertEquals(1, scheduler.delayed.size)

        assertEquals(
            1,
            states.count {
                it.state ==
                    AccountSessionState.RETRY_WAIT
            }
        )
    }

    @Test
    fun stopCancelsScheduledReconnect() {
        connect().fail()

        manager.stop()

        scheduler.advanceBy(600000L)

        assertState(AccountSessionState.STOPPED)

        assertEquals(1, keys.size)
        assertEquals(1, sockets.size)
        assertTrue(scheduler.delayed.isEmpty())
    }

    @Test
    fun staleRetryCannotRestartStoppedSession() {
        connect().fail()

        val stale =
            scheduler.delayed.single().action

        manager.stop()

        stale.invoke()

        assertState(AccountSessionState.STOPPED)

        assertEquals(1, keys.size)
        assertTrue(scheduler.delayed.isEmpty())
    }

    @Test
    fun existingCooldownPreventsKeyClientCreation() {
        guard.blockFor(120000L)

        manager.start()

        assertState(AccountSessionState.RETRY_WAIT)

        assertTrue(keys.isEmpty())
        assertEquals(0, worker.queued)

        scheduler.advanceBy(119999L)

        assertTrue(keys.isEmpty())

        scheduler.advanceBy(1L)

        assertState(
            AccountSessionState.ACQUIRING_KEY
        )

        assertEquals(1, keys.size)
    }

    @Test
    fun cooldownAddedBeforeWorkerPreventsRest() {
        manager.start()

        guard.blockFor(120000L)

        completeRest()

        assertState(AccountSessionState.RETRY_WAIT)

        assertEquals(0, keys.single().startCalls)
        assertEquals(1, keys.single().closeCalls)
        assertTrue(sockets.isEmpty())
    }

    @Test
    fun cooldownAddedBeforeResultPreventsSocket() {
        manager.start()

        worker.runNext()

        guard.blockFor(120000L)

        scheduler.drain()

        assertState(AccountSessionState.RETRY_WAIT)

        assertEquals(1, keys.single().startCalls)
        assertTrue(sockets.isEmpty())
    }

    @Test
    fun restRateLimitPreservesServerCooldown() {
        configureKey = {
            it.startAction = {
                throw AccountListenKeyError(
                    explanation = "fake rate limit",
                    retryAfterMs = 300000L
                )
            }
        }

        manager.start()
        completeRest()

        assertState(AccountSessionState.RETRY_WAIT)

        assertEquals(300000L, guard.remainingMs())

        scheduler.advanceBy(299999L)

        assertEquals(1, keys.size)

        configureKey = {}

        scheduler.advanceBy(1L)

        assertEquals(2, keys.size)

        assertState(
            AccountSessionState.ACQUIRING_KEY
        )
    }

    @Test
    fun socket429PreservesRetryAfter() {
        connect().fail(
            AccountSocketFailure(
                httpStatus = 429,
                retryAfter = "600"
            )
        )

        assertState(AccountSessionState.RETRY_WAIT)

        assertEquals(600000L, guard.remainingMs())

        scheduler.advanceBy(599999L)

        assertEquals(1, keys.size)

        scheduler.advanceBy(1L)

        assertEquals(2, keys.size)
    }

    @Test
    fun socket418PreservesRetryAfter() {
        connect().fail(
            AccountSocketFailure(
                httpStatus = 418,
                retryAfter = "900"
            )
        )

        assertState(AccountSessionState.RETRY_WAIT)

        assertEquals(900000L, guard.remainingMs())

        scheduler.advanceBy(899999L)

        assertEquals(1, keys.size)

        scheduler.advanceBy(1L)

        assertEquals(2, keys.size)
    }

    @Test
    fun extendedCooldownIsRecheckedAtWakeup() {
        guard.blockFor(120000L)

        manager.start()

        scheduler.advanceBy(60000L)

        guard.blockFor(300000L)

        scheduler.advanceBy(299999L)

        assertState(AccountSessionState.RETRY_WAIT)

        assertTrue(keys.isEmpty())

        scheduler.advanceBy(1L)

        assertEquals(1, keys.size)
    }

    @Test
    fun stopAndRestartCannotBypassCooldown() {
        connect().fail(
            AccountSocketFailure(
                httpStatus = 429,
                retryAfter = "600"
            )
        )

        manager.stop()

        manager.start()

        assertState(AccountSessionState.RETRY_WAIT)

        assertEquals(1, keys.size)

        scheduler.advanceBy(599999L)

        assertEquals(1, keys.size)

        scheduler.advanceBy(1L)

        assertEquals(2, keys.size)
    }

    @Test
    fun longCooldownUsesBoundedSchedulingIntervals() {
        guard.blockFor(86400000L)

        manager.start()

        repeat(10) {
            val delay =
                scheduler.delayed.single().due -
                    scheduler.now

            assertEquals(60000L, delay)

            scheduler.advanceBy(delay)

            assertState(
                AccountSessionState.RETRY_WAIT
            )

            assertTrue(keys.isEmpty())
        }

        assertEquals(
            85800000L,
            guard.remainingMs()
        )
    }

    @Test
    fun keepAliveDoesNotRunBeforeInterval() {
        connect()

        scheduler.advanceBy(
            AccountSessionPolicy.KEEPALIVE_MS - 1L
        )

        assertState(AccountSessionState.CONNECTED)
        assertEquals(0, worker.queued)
        assertEquals(0, keys.single().keepAliveCalls)
    }

    @Test
    fun keepAliveIsDelegatedToWorker() {
        connect()

        scheduler.advanceBy(
            AccountSessionPolicy.KEEPALIVE_MS
        )

        assertEquals(1, worker.queued)
        assertEquals(0, keys.single().keepAliveCalls)

        completeRest()

        assertEquals(1, keys.single().keepAliveCalls)
        assertState(AccountSessionState.CONNECTED)
    }

    @Test
    fun keepAliveReceivesCurrentListenKey() {
        var received: String? = null

        configureKey = {
            it.keepAliveAction = { key ->
                received = key
                key
            }
        }

        val socket = connect()

        scheduler.advanceBy(
            AccountSessionPolicy.KEEPALIVE_MS
        )

        completeRest()

        assertEquals(socket.key, received)
        assertEquals(1, keys.single().keepAliveCalls)
    }

    @Test
    fun successfulKeepAlivePreservesSocketAndGeneration() {
        val socket = connect()
        val generation = manager.snapshot().generation

        scheduler.advanceBy(
            AccountSessionPolicy.KEEPALIVE_MS
        )

        completeRest()

        assertState(AccountSessionState.CONNECTED)
        assertEquals(generation, manager.snapshot().generation)
        assertSame(socket, sockets.single())
        assertEquals(0, socket.closeCalls)
        assertEquals(0, keys.single().closeCalls)
    }

    @Test
    fun successfulKeepAliveExtendsKeyLifetime() {
        val socket = connect()

        scheduler.advanceBy(
            AccountSessionPolicy.KEEPALIVE_MS
        )

        completeRest()

        scheduler.advanceBy(
            AccountSessionPolicy.KEY_MAX_AGE_MS -
                AccountSessionPolicy.KEEPALIVE_MS
        )

        socket.event(accountEvent)

        assertState(AccountSessionState.CONNECTED)
        assertEquals(1, events.size)
        assertEquals(1, keys.single().keepAliveCalls)
    }

    @Test
    fun secondKeepAliveUsesRenewedTimestamp() {
        connect()

        scheduler.advanceBy(
            AccountSessionPolicy.KEEPALIVE_MS
        )

        completeRest()

        scheduler.advanceBy(
            AccountSessionPolicy.KEEPALIVE_MS - 1L
        )

        assertEquals(0, worker.queued)
        assertEquals(1, keys.single().keepAliveCalls)

        scheduler.advanceBy(1L)

        assertEquals(1, worker.queued)

        completeRest()

        assertEquals(2, keys.single().keepAliveCalls)
        assertState(AccountSessionState.CONNECTED)
    }

    @Test
    fun pendingKeepAlivePreventsDuplicateRestTasks() {
        connect()

        scheduler.advanceBy(
            AccountSessionPolicy.KEEPALIVE_MS
        )

        assertEquals(1, worker.queued)

        scheduler.advanceBy(600000L)

        assertEquals(1, worker.queued)
        assertEquals(0, keys.single().keepAliveCalls)
        assertEquals(1, scheduler.delayed.size)

        completeRest()

        assertEquals(1, keys.single().keepAliveCalls)
        assertState(AccountSessionState.CONNECTED)
    }

    @Test
    fun changedListenKeyRequiresFreshSession() {
        configureKey = {
            it.keepAliveAction = {
                "replacement-key"
            }
        }

        val socket = connect()
        val generation = manager.snapshot().generation

        scheduler.advanceBy(
            AccountSessionPolicy.KEEPALIVE_MS
        )

        completeRest()

        assertState(AccountSessionState.RETRY_WAIT)
        assertEquals(1, socket.closeCalls)
        assertEquals(1, keys.single().closeCalls)

        configureKey = {
            it.startAction = {
                "replacement-key"
            }
        }

        scheduler.advanceBy(2000L)

        completeRest()

        assertEquals(2, sockets.size)
        assertEquals(2, keys.size)
        assertEquals("replacement-key", sockets.last().key)

        assertEquals(
            generation + 1L,
            manager.snapshot().generation
        )

        sockets.last().open()

        assertState(AccountSessionState.CONNECTED)
    }

    @Test
    fun keepAliveFailureClosesCurrentSession() {
        configureKey = {
            it.keepAliveAction = {
                throw IllegalStateException()
            }
        }

        val socket = connect()

        scheduler.advanceBy(
            AccountSessionPolicy.KEEPALIVE_MS
        )

        completeRest()

        assertState(AccountSessionState.RETRY_WAIT)
        assertEquals(1, socket.closeCalls)
        assertEquals(1, keys.single().closeCalls)
        assertEquals(1, scheduler.delayed.size)
    }

    @Test
    fun expiredKeepAliveErrorRequiresReconnect() {
        configureKey = {
            it.keepAliveAction = {
                throw AccountListenKeyError(
                    explanation = "fake expired session",
                    sessionExpired = true
                )
            }
        }

        val socket = connect()

        scheduler.advanceBy(
            AccountSessionPolicy.KEEPALIVE_MS
        )

        completeRest()

        assertState(AccountSessionState.RETRY_WAIT)
        assertEquals(1, socket.closeCalls)

        configureKey = {}

        scheduler.advanceBy(2000L)

        completeRest()

        assertEquals(2, keys.size)
        assertEquals(2, sockets.size)
    }

    @Test
    fun keepAliveRateLimitPreservesCooldown() {
        configureKey = {
            it.keepAliveAction = {
                throw AccountListenKeyError(
                    explanation = "fake rate limit",
                    retryAfterMs = 300000L
                )
            }
        }

        connect()

        scheduler.advanceBy(
            AccountSessionPolicy.KEEPALIVE_MS
        )

        completeRest()

        assertState(AccountSessionState.RETRY_WAIT)
        assertEquals(300000L, guard.remainingMs())

        scheduler.advanceBy(299999L)

        assertEquals(1, keys.size)

        scheduler.advanceBy(1L)

        assertEquals(2, keys.size)
        assertState(AccountSessionState.ACQUIRING_KEY)
    }

    @Test
    fun activeCooldownDefersKeepAlive() {
        connect()

        scheduler.advanceBy(
            AccountSessionPolicy.KEEPALIVE_MS - 60000L
        )

        guard.blockFor(180000L)

        scheduler.advanceBy(60000L)

        assertState(AccountSessionState.CONNECTED)
        assertEquals(0, worker.queued)

        scheduler.advanceBy(119999L)

        assertEquals(0, worker.queued)

        scheduler.advanceBy(1L)

        assertEquals(1, worker.queued)

        completeRest()

        assertEquals(1, keys.single().keepAliveCalls)
        assertState(AccountSessionState.CONNECTED)
    }

    @Test
    fun cooldownAddedBeforeKeepAliveWorkerPreventsRequest() {
        connect()

        scheduler.advanceBy(
            AccountSessionPolicy.KEEPALIVE_MS
        )

        assertEquals(1, worker.queued)

        guard.blockFor(120000L)

        completeRest()

        assertEquals(0, keys.single().keepAliveCalls)
        assertState(AccountSessionState.RETRY_WAIT)
        assertEquals(120000L, guard.remainingMs())
    }

    @Test
    fun keyExpiresWhenCooldownPreventsRenewal() {
        val socket = connect()

        guard.blockFor(
            AccountSessionPolicy.KEY_MAX_AGE_MS + 60000L
        )

        scheduler.advanceBy(
            AccountSessionPolicy.KEY_MAX_AGE_MS - 1L
        )

        assertState(AccountSessionState.CONNECTED)

        scheduler.advanceBy(1L)

        assertState(AccountSessionState.RETRY_WAIT)
        assertEquals(0, keys.single().keepAliveCalls)
        assertEquals(1, socket.closeCalls)
        assertEquals(1, keys.size)
    }

    @Test
    fun expiredKeyRejectsEventBeforeMaintenanceRuns() {
        val socket = connect()

        scheduler.now =
            AccountSessionPolicy.KEY_MAX_AGE_MS

        socket.event(accountEvent)

        assertTrue(events.isEmpty())
        assertState(AccountSessionState.RETRY_WAIT)
        assertEquals(1, socket.closeCalls)
    }

    @Test
    fun lateKeepAliveResultCannotReviveExpiredKey() {
        val socket = connect()

        scheduler.advanceBy(
            AccountSessionPolicy.KEEPALIVE_MS
        )

        worker.runNext()

        assertEquals(1, scheduler.ready.size)

        scheduler.now =
            AccountSessionPolicy.KEY_MAX_AGE_MS

        scheduler.drain()

        assertState(AccountSessionState.RETRY_WAIT)
        assertEquals(1, socket.closeCalls)
        assertEquals(1, keys.single().keepAliveCalls)
    }

    @Test
    fun stopCancelsPendingKeepAlive() {
        val socket = connect()

        scheduler.advanceBy(
            AccountSessionPolicy.KEEPALIVE_MS
        )

        assertEquals(1, worker.queued)

        manager.stop()

        completeRest()

        assertState(AccountSessionState.STOPPED)
        assertEquals(0, keys.single().keepAliveCalls)
        assertEquals(1, socket.closeCalls)
        assertTrue(scheduler.delayed.isEmpty())
    }

    @Test
    fun staleKeepAliveResultCannotChangeNewSession() {
        val oldSocket = connect()

        scheduler.advanceBy(
            AccountSessionPolicy.KEEPALIVE_MS
        )

        worker.runNext()

        val stale = scheduler.ready.removeFirst()

        manager.stop()

        val newSocket = connect()
        val generation = manager.snapshot().generation

        stale.invoke()

        assertState(AccountSessionState.CONNECTED)
        assertEquals(generation, manager.snapshot().generation)
        assertEquals(1, oldSocket.closeCalls)
        assertEquals(0, newSocket.closeCalls)
        assertEquals(1, scheduler.delayed.size)
    }

    @Test
    fun socketRotatesAtMaximumAgeDespiteSuccessfulRenewals() {
        val socket = connect()

        val interval =
            AccountSessionPolicy.KEEPALIVE_MS

        val rotationAge =
            AccountSessionPolicy.SOCKET_MAX_AGE_MS

        val renewals =
            (rotationAge / interval).toInt() - 1

        repeat(renewals) {
            scheduler.advanceBy(interval)

            completeRest()

            assertState(AccountSessionState.CONNECTED)
        }

        assertEquals(
            renewals,
            keys.single().keepAliveCalls
        )

        scheduler.advanceBy(
            rotationAge - scheduler.now - 1L
        )

        assertState(AccountSessionState.CONNECTED)
        assertEquals(0, socket.closeCalls)

        scheduler.advanceBy(1L)

        assertState(AccountSessionState.RETRY_WAIT)
        assertEquals(1, socket.closeCalls)
        assertEquals(1, keys.single().closeCalls)

        scheduler.advanceBy(2000L)

        completeRest()

        assertEquals(2, sockets.size)
        assertEquals(2, keys.size)

        sockets.last().open()

        assertState(AccountSessionState.CONNECTED)
    }

    @Test
    fun delayedConnectionRejectsExpiredInitialKey() {
        manager.start()

        completeRest()

        assertState(AccountSessionState.CONNECTING)

        val socket = sockets.single()

        scheduler.now =
            AccountSessionPolicy.KEY_MAX_AGE_MS

        socket.open()

        assertState(AccountSessionState.RETRY_WAIT)
        assertEquals(1, socket.closeCalls)

        assertFalse(
            states.any {
                it.state == AccountSessionState.CONNECTED
            }
        )
    }

    /**
     * Separate scheduler for actual worker threads.
     *
     * Queue mutations are synchronized. Controller
     * actions always execute outside the queue lock.
     */
    private class ConcurrentScheduler : Scheduler {

        private val lock = Any()

        private val ready =
            ArrayDeque<() -> Unit>()

        private val delayed =
            ArrayDeque<() -> Unit>()

        val now = AtomicLong(0L)

        val posts = AtomicInteger(0)

        val readyCount: Int
            get() = synchronized(lock) {
                ready.size
            }

        val delayedCount: Int
            get() = synchronized(lock) {
                delayed.size
            }

        override fun post(action: () -> Unit) {
            synchronized(lock) {
                ready.addLast(action)
                posts.incrementAndGet()
            }
        }

        override fun postDelayed(
            delayMs: Long,
            action: () -> Unit
        ) {
            require(delayMs >= 0L)

            synchronized(lock) {
                delayed.addLast(action)
            }
        }

        override fun cancelAll() {
            synchronized(lock) {
                ready.clear()
                delayed.clear()
            }
        }

        fun drain() {
            var count = 0

            while (true) {
                val action = synchronized(lock) {
                    ready.pollFirst()
                } ?: return

                check(++count <= 1000)

                action()
            }
        }

        fun runMaintenanceAt(time: Long) {
            now.set(time)

            val action = synchronized(lock) {
                check(delayed.isNotEmpty())
                delayed.removeFirst()
            }

            action()
        }
    }

    /**
     * Deliberately simulates a REST operation that
     * returns even after cancellation or interruption.
     */
    private class BlockingRest {

        val entered = CountDownLatch(1)

        val release = CountDownLatch(1)

        fun awaitEntered() {
            assertTrue(
                "REST worker did not enter",
                entered.await(5, TimeUnit.SECONDS)
            )
        }

        fun block() {
            entered.countDown()

            val deadline =
                System.nanoTime() +
                    TimeUnit.SECONDS.toNanos(10)

            while (true) {
                val remaining =
                    deadline - System.nanoTime()

                check(remaining > 0L)

                try {
                    check(
                        release.await(
                            remaining,
                            TimeUnit.NANOSECONDS
                        )
                    )

                    return

                } catch (_: InterruptedException) {
                    // Simulate a late network result.
                }
            }
        }
    }

    private class ConcurrentFixture : AutoCloseable {

        val scheduler = ConcurrentScheduler()

        val worker =
            Executors.newSingleThreadExecutor()

        val guard = AccountRateLimitGuard(
            elapsed = { scheduler.now.get() }
        )

        val gate = BlockingRest()

        val keyCreates = AtomicInteger(0)

        val keyCloses = AtomicInteger(0)

        val startCalls = AtomicInteger(0)

        val renewalCalls = AtomicInteger(0)

        val sockets = mutableListOf<FakeSocket>()

        var blockInitial = true

        var blockRenewal = false

        var lateRateLimit = false

        val manager = AccountSessionManager(
            keyFactory = AccountSessionKeyFactory {
                val identity =
                    keyCreates.incrementAndGet()

                val shouldBlock =
                    blockInitial && identity == 1

                val shouldLimit = lateRateLimit

                object : AccountSessionKeyAccess {

                    override fun start(): String {
                        startCalls.incrementAndGet()

                        if (shouldBlock) {
                            gate.block()

                            if (shouldLimit) {
                                throw AccountListenKeyError(
                                    "Rate limited",
                                    retryAfterMs = 300000L
                                )
                            }
                        }

                        return "fake-key-$identity"
                    }

                    override fun keepAlive(
                        expectedKey: String
                    ): String {
                        renewalCalls.incrementAndGet()

                        if (blockRenewal) {
                            gate.block()
                        }

                        return expectedKey
                    }

                    override fun close() {
                        keyCloses.incrementAndGet()
                    }
                }
            },
            socketFactory = AccountSessionSocketFactory {
                key,
                onStatus,
                onEvent,
                onFailure ->

                FakeSocket(
                    key,
                    onStatus,
                    onEvent,
                    onFailure
                ).also {
                    sockets.add(it)
                }
            },
            scheduler = scheduler,
            rateLimitGuard = guard,
            worker = worker,
            elapsed = { scheduler.now.get() },
            jitterPercent = { 0 }
        )

        fun awaitWorker() {
            // A barrier queued behind all previous
            // REST work proves that it has returned.
            worker.submit {}.get(
                5,
                TimeUnit.SECONDS
            )
        }

        fun assertState(
            expected: AccountSessionState
        ) {
            assertEquals(
                expected,
                manager.snapshot().state
            )
        }

        override fun close() {
            gate.release.countDown()

            try {
                manager.close()
            } finally {
                worker.shutdownNow()

                assertTrue(
                    "Worker did not terminate",
                    worker.awaitTermination(
                        5,
                        TimeUnit.SECONDS
                    )
                )
            }
        }
    }

    @Test
    fun lateRestAfterStopDoesNotPublishCallback() {
        ConcurrentFixture().use { f ->
            f.manager.start()
            f.gate.awaitEntered()

            f.manager.stop()

            f.assertState(
                AccountSessionState.STOPPED
            )

            f.gate.release.countDown()
            f.awaitWorker()

            assertEquals(0, f.scheduler.posts.get())
            assertEquals(0, f.scheduler.readyCount)
            assertEquals(0, f.scheduler.delayedCount)
            assertEquals(1, f.keyCloses.get())
            assertTrue(f.sockets.isEmpty())

            f.assertState(
                AccountSessionState.STOPPED
            )
        }
    }

    @Test
    fun lateRestAfterCloseDoesNotPublishCallback() {
        ConcurrentFixture().use { f ->
            f.manager.start()
            f.gate.awaitEntered()

            f.manager.close()

            f.gate.release.countDown()

            assertTrue(
                f.worker.awaitTermination(
                    5,
                    TimeUnit.SECONDS
                )
            )

            assertEquals(0, f.scheduler.posts.get())
            assertEquals(0, f.scheduler.readyCount)
            assertEquals(0, f.scheduler.delayedCount)
            assertEquals(1, f.keyCloses.get())
            assertTrue(f.sockets.isEmpty())

            f.manager.start()

            f.assertState(
                AccountSessionState.CLOSED
            )
        }
    }

    @Test
    fun lateRestAfterRestartCannotReplaceNewSession() {
        ConcurrentFixture().use { f ->
            f.manager.start()
            f.gate.awaitEntered()

            val previous =
                f.manager.snapshot().generation

            f.manager.stop()
            f.manager.start()

            val current =
                f.manager.snapshot().generation

            assertTrue(current > previous)

            f.gate.release.countDown()
            f.awaitWorker()

            assertEquals(1, f.scheduler.posts.get())
            assertEquals(1, f.scheduler.readyCount)

            f.scheduler.drain()

            assertEquals(2, f.startCalls.get())
            assertEquals(1, f.keyCloses.get())
            assertEquals(1, f.sockets.size)

            val socket = f.sockets.single()

            assertEquals("fake-key-2", socket.key)

            socket.open()

            f.assertState(
                AccountSessionState.CONNECTED
            )

            assertEquals(
                current,
                f.manager.snapshot().generation
            )
        }
    }

    @Test
    fun cancelledRestStillRecordsRateLimit() {
        ConcurrentFixture().use { f ->
            f.lateRateLimit = true

            f.manager.start()
            f.gate.awaitEntered()

            f.manager.stop()

            f.gate.release.countDown()
            f.awaitWorker()

            assertEquals(
                300000L,
                f.guard.remainingMs()
            )

            assertEquals(0, f.scheduler.posts.get())
            assertEquals(0, f.scheduler.readyCount)

            f.manager.start()

            f.assertState(
                AccountSessionState.RETRY_WAIT
            )

            assertEquals(1, f.keyCreates.get())
            assertEquals(1, f.startCalls.get())
            assertTrue(f.sockets.isEmpty())
        }
    }

    @Test
    fun stopClearsCallbackPostedByRealWorker() {
        ConcurrentFixture().use { f ->
            f.blockInitial = false

            f.manager.start()
            f.awaitWorker()

            assertEquals(1, f.scheduler.posts.get())
            assertEquals(1, f.scheduler.readyCount)

            f.manager.stop()

            assertEquals(0, f.scheduler.readyCount)
            assertEquals(0, f.scheduler.delayedCount)

            f.scheduler.drain()

            assertTrue(f.sockets.isEmpty())
            assertEquals(1, f.keyCloses.get())

            f.assertState(
                AccountSessionState.STOPPED
            )
        }
    }

    @Test
    fun lateKeepAliveAfterStopDoesNotPublishCallback() {
        ConcurrentFixture().use { f ->
            f.blockInitial = false
            f.blockRenewal = true

            f.manager.start()
            f.awaitWorker()
            f.scheduler.drain()

            val socket = f.sockets.single()

            socket.open()

            f.assertState(
                AccountSessionState.CONNECTED
            )

            assertEquals(1, f.scheduler.posts.get())

            f.scheduler.runMaintenanceAt(
                AccountSessionPolicy.KEEPALIVE_MS
            )

            f.gate.awaitEntered()

            assertEquals(1, f.renewalCalls.get())

            f.manager.stop()

            f.gate.release.countDown()
            f.awaitWorker()

            assertEquals(1, f.scheduler.posts.get())
            assertEquals(0, f.scheduler.readyCount)
            assertEquals(0, f.scheduler.delayedCount)
            assertEquals(1, socket.closeCalls)
            assertEquals(1, f.keyCloses.get())

            f.assertState(
                AccountSessionState.STOPPED
            )
        }
    }

    @Test
    fun keyExpiredDuringConnectingObserverPreventsSocket() {
        stateObserver = { snapshot ->
            if (
                snapshot.state ==
                    AccountSessionState.CONNECTING
            ) {
                scheduler.now =
                    AccountSessionPolicy.KEY_MAX_AGE_MS
            }
        }

        manager.start()
        completeRest()

        assertState(AccountSessionState.RETRY_WAIT)
        assertTrue(sockets.isEmpty())
        assertEquals(1, keys.single().closeCalls)
        assertEquals(1, scheduler.delayed.size)
    }

    @Test
    fun unalignedKeyExpirationUsesExactDeadline() {
        manager.start()
        completeRest()

        scheduler.advanceBy(12345L)

        val socket = sockets.single()
        socket.open()

        guard.blockFor(
            AccountSessionPolicy.KEY_MAX_AGE_MS +
                60000L
        )

        scheduler.advanceBy(
            AccountSessionPolicy.KEY_MAX_AGE_MS -
                scheduler.now - 1L
        )

        assertState(AccountSessionState.CONNECTED)
        assertEquals(0, socket.closeCalls)
        assertEquals(0, worker.queued)

        scheduler.advanceBy(1L)

        assertState(AccountSessionState.RETRY_WAIT)
        assertEquals(1, socket.closeCalls)
        assertEquals(1, keys.single().closeCalls)
        assertEquals(0, keys.single().keepAliveCalls)
    }

    @Test
    fun unalignedConnectionRenewsAtKeyDeadline() {
        manager.start()
        completeRest()

        scheduler.advanceBy(12345L)

        sockets.single().open()

        assertState(AccountSessionState.CONNECTED)

        scheduler.advanceBy(
            AccountSessionPolicy.KEEPALIVE_MS -
                scheduler.now - 1L
        )

        assertEquals(0, worker.queued)
        assertEquals(0, keys.single().keepAliveCalls)

        scheduler.advanceBy(1L)

        assertEquals(1, worker.queued)

        completeRest()

        assertEquals(1, keys.single().keepAliveCalls)
        assertState(AccountSessionState.CONNECTED)
    }

    @Test
    fun unalignedCooldownEndsAtRenewalDeadline() {
        connect()

        scheduler.advanceBy(
            AccountSessionPolicy.KEEPALIVE_MS -
                60000L
        )

        guard.blockFor(72345L)

        scheduler.advanceBy(60000L)

        assertEquals(0, worker.queued)

        assertEquals(
            12345L,
            scheduler.delayed.single().due -
                scheduler.now
        )

        scheduler.advanceBy(12344L)

        assertEquals(0, worker.queued)

        scheduler.advanceBy(1L)

        assertEquals(1, worker.queued)

        completeRest()

        assertEquals(1, keys.single().keepAliveCalls)
        assertState(AccountSessionState.CONNECTED)
    }

    @Test
    fun stableBackoffResetsWithoutMaintenanceCallback() {
        connect().fail()

        scheduler.advanceBy(2000L)
        completeRest()

        sockets.last().open()

        assertState(AccountSessionState.CONNECTED)

        scheduler.now += 60000L

        sockets.last().fail()

        assertState(AccountSessionState.RETRY_WAIT)

        assertEquals(
            2000L,
            scheduler.delayed.single().due -
                scheduler.now
        )
    }

    @Test
    fun almostStableConnectionPreservesBackoff() {
        connect().fail()

        scheduler.advanceBy(2000L)
        completeRest()

        sockets.last().open()

        scheduler.now += 59999L

        sockets.last().fail()

        assertState(AccountSessionState.RETRY_WAIT)

        assertEquals(
            4000L,
            scheduler.delayed.single().due -
                scheduler.now
        )
    }

    @Test
    fun expiredKeyPreventsQueuedKeepAliveRequest() {
        val socket = connect()

        scheduler.advanceBy(
            AccountSessionPolicy.KEEPALIVE_MS
        )

        assertEquals(1, worker.queued)

        scheduler.now =
            AccountSessionPolicy.KEY_MAX_AGE_MS

        completeRest()

        assertEquals(0, keys.single().keepAliveCalls)
        assertState(AccountSessionState.RETRY_WAIT)
        assertEquals(1, socket.closeCalls)
        assertEquals(1, keys.single().closeCalls)
    }

    @Test
    fun expiredSocketPreventsQueuedKeepAliveRequest() {
        val interval =
            AccountSessionPolicy.KEEPALIVE_MS

        val rotationAge =
            AccountSessionPolicy.SOCKET_MAX_AGE_MS

        val socketStartedAt = 12345L

        manager.start()

        worker.drain()

        scheduler.now = socketStartedAt
        scheduler.drain()

        val socket = sockets.single()

        socket.open()

        assertState(AccountSessionState.CONNECTED)

        val renewals =
            (rotationAge / interval).toInt() - 1

        repeat(renewals) { index ->
            val deadline =
                (index + 1L) * interval

            scheduler.advanceBy(
                deadline - scheduler.now
            )

            assertEquals(1, worker.queued)

            completeRest()

            assertState(AccountSessionState.CONNECTED)
        }

        assertEquals(
            renewals,
            keys.single().keepAliveCalls
        )

        val lastRenewedAt = scheduler.now

        scheduler.advanceBy(interval)

        assertEquals(1, worker.queued)

        scheduler.now =
            socketStartedAt + rotationAge

        // Isolate socket expiration: the listen key
        // must still be valid when the worker runs.
        assertTrue(
            AccountSessionPolicy.isKeyUsable(
                lastRenewedAt,
                scheduler.now
            )
        )

        assertFalse(
            AccountSessionPolicy.isSocketUsable(
                socketStartedAt,
                scheduler.now
            )
        )

        completeRest()

        assertEquals(
            renewals,
            keys.single().keepAliveCalls
        )

        assertState(AccountSessionState.RETRY_WAIT)
        assertEquals(1, socket.closeCalls)
        assertEquals(1, keys.single().closeCalls)
    }

    @Test
    fun delayedKeepAliveWorkerReschedulesExactDeadline() {
        val socket = connect()

        val interval =
            AccountSessionPolicy.KEEPALIVE_MS

        scheduler.advanceBy(interval)

        assertEquals(1, worker.queued)

        scheduler.advanceBy(12345L)

        completeRest()

        assertEquals(1, keys.single().keepAliveCalls)
        assertEquals(1, scheduler.delayed.size)

        scheduler.advanceBy(interval - 1L)

        assertEquals(0, worker.queued)
        assertEquals(1, keys.single().keepAliveCalls)

        scheduler.advanceBy(1L)

        assertEquals(1, worker.queued)

        completeRest()

        assertEquals(2, keys.single().keepAliveCalls)
        assertEquals(1, scheduler.delayed.size)
        assertEquals(0, socket.closeCalls)
        assertState(AccountSessionState.CONNECTED)
    }

    @Test
    fun successfulRenewalReplacesOldMaintenanceCallback() {
        val socket = connect()
        val generation = manager.snapshot().generation

        scheduler.advanceBy(
            AccountSessionPolicy.KEEPALIVE_MS
        )

        assertEquals(1, worker.queued)

        val previous =
            scheduler.delayed.single()

        completeRest()

        val current =
            scheduler.delayed.single()

        assertNotSame(previous, current)
        assertEquals(1, scheduler.delayed.size)
        assertEquals(0, scheduler.ready.size)
        assertEquals(0, worker.queued)
        assertEquals(1, keys.single().keepAliveCalls)
        assertEquals(0, socket.closeCalls)

        assertEquals(
            generation,
            manager.snapshot().generation
        )

        assertState(AccountSessionState.CONNECTED)
    }

    @Test
    fun keyExpiredDuringSocketConstructionPreventsConnect() {
        configureSocket = {
            scheduler.now =
                AccountSessionPolicy.KEY_MAX_AGE_MS
        }

        manager.start()
        completeRest()

        assertState(AccountSessionState.RETRY_WAIT)

        val socket = sockets.single()

        assertEquals(0, socket.connectCalls)
        assertEquals(1, socket.closeCalls)
        assertEquals(1, keys.single().closeCalls)
        assertEquals(0, worker.queued)
        assertEquals(1, scheduler.delayed.size)
    }
}
