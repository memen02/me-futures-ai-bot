package com.mefuturesaibot.app.account

import com.mefuturesaibot.app.BuildConfig
import com.mefuturesaibot.app.demo.FuturesVenue
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/**
 * Display-only balance controller behavior tests.
 *
 * Uses virtual elapsed time and an actual serial
 * JVM worker. No network or trading operations.
 */
class AccountBalanceControllerTest {

    @Test
    fun initialStateIsStopped() {
        AccountBalanceControllerFixture().use { f ->
            f.assertStatus(AccountBalanceStatus.STOPPED)
            f.assertNoVisibleBalance()

            assertEquals(0L, f.generation)
            assertEquals(0, f.readCalls.get())
            assertEquals(0, f.factoryCalls.get())
            assertEquals(0, f.scheduler.delayedCount)
            assertTrue(f.states.isEmpty())
        }
    }

    @Test
    fun startSchedulesInitialRead() {
        AccountBalanceControllerFixture().use { f ->
            f.controller.start()

            f.assertStatus(AccountBalanceStatus.LOADING)

            assertEquals(1L, f.generation)
            assertEquals(0L, f.scheduler.nextDue)
            assertEquals(1, f.scheduler.delayedCount)
            assertEquals(0, f.readCalls.get())
        }
    }

    @Test
    fun initialReadPublishesBalance() {
        AccountBalanceControllerFixture().use { f ->
            f.startReady()

            val snapshot = f.controller.snapshot()

            assertEquals(FuturesVenue.DEMO, snapshot.venue)
            assertEquals(f.balance(), snapshot.balance)

            assertEquals(
                BigDecimal("105.00"),
                snapshot.freshBalance(f.scheduler.now)?.equity
            )

            assertFalse(snapshot.invalidated)
            assertFalse(snapshot.requestPending)
            assertEquals(1, f.readCalls.get())
            assertEquals(1, f.factoryCalls.get())
        }
    }

    @Test
    fun duplicateStartDoesNotReplaceSession() {
        AccountBalanceControllerFixture().use { f ->
            f.startReady()

            val generation = f.generation
            val publications = f.states.size

            repeat(10) {
                f.controller.start()
            }

            assertEquals(generation, f.generation)
            assertEquals(publications, f.states.size)
            assertEquals(1, f.readCalls.get())
            assertEquals(1, f.factoryCalls.get())
            assertEquals(1, f.scheduler.delayedCount)
        }
    }

    @Test
    fun normalRefreshWaitsTenSeconds() {
        AccountBalanceControllerFixture().use { f ->
            f.startReady()

            assertEquals(10000L, f.scheduler.nextDue)

            f.scheduler.advanceBy(9999L)

            assertEquals(1, f.readCalls.get())

            f.scheduler.advanceBy(1L)
            f.completeRest()

            f.assertStatus(AccountBalanceStatus.READY)

            assertEquals(2, f.readCalls.get())
            assertEquals(1, f.factoryCalls.get())
            assertEquals(20000L, f.scheduler.nextDue)
        }
    }

    @Test
    fun refreshReplacesPreviousBalance() {
        AccountBalanceControllerFixture().use { f ->
            f.startReady()

            f.readAction = {
                f.balance(
                    wallet = "110.00",
                    equity = "115.00"
                )
            }

            f.scheduler.advanceBy(10000L)
            f.completeRest()

            val balance = f.controller.snapshot()
                .freshBalance(f.scheduler.now)

            assertEquals(
                BigDecimal("115.00"),
                balance?.equity
            )

            assertEquals(
                BigDecimal("110.00"),
                balance?.wallet
            )

            assertEquals(10000L, balance?.receivedElapsed)
        }
    }

    @Test
    fun invalidationImmediatelyHidesBalance() {
        AccountBalanceControllerFixture().use { f ->
            f.startReady()

            f.invalidate()

            f.assertStatus(AccountBalanceStatus.STALE)
            f.assertNoVisibleBalance()

            assertTrue(
                f.controller.snapshot().invalidated
            )

            assertEquals(3000L, f.scheduler.nextDue)
            assertEquals(1, f.readCalls.get())
        }
    }

    @Test
    fun invalidationHonorsMinimumReadInterval() {
        AccountBalanceControllerFixture().use { f ->
            f.startReady()

            f.invalidate()

            f.scheduler.advanceBy(2999L)

            assertEquals(1, f.readCalls.get())

            f.scheduler.advanceBy(1L)
            f.completeRest()

            assertEquals(2, f.readCalls.get())

            f.assertStatus(AccountBalanceStatus.READY)
        }
    }

    @Test
    fun eventUsesFiveHundredMillisecondDebounce() {
        AccountBalanceControllerFixture().use { f ->
            f.startReady()

            f.scheduler.advanceBy(4000L)

            f.event(AccountStreamEventType.ACCOUNT_CHANGED)

            assertEquals(4500L, f.scheduler.nextDue)

            f.scheduler.advanceBy(499L)

            assertEquals(1, f.readCalls.get())

            f.scheduler.advanceBy(1L)
            f.completeRest()

            assertEquals(2, f.readCalls.get())

            f.assertStatus(AccountBalanceStatus.READY)
        }
    }

    @Test
    fun eventFloodDoesNotPostponeExistingWakeup() {
        AccountBalanceControllerFixture().use { f ->
            f.startReady()

            f.scheduler.advanceBy(4000L)
            f.invalidate()

            val posts = f.scheduler.delayedPosts.get()

            repeat(100) {
                f.invalidate()
            }

            f.scheduler.advanceBy(200L)

            repeat(100) {
                f.invalidate()
            }

            assertEquals(4500L, f.scheduler.nextDue)
            assertEquals(1, f.scheduler.delayedCount)

            assertEquals(
                posts,
                f.scheduler.delayedPosts.get()
            )

            f.scheduler.advanceBy(300L)
            f.completeRest()

            assertEquals(2, f.readCalls.get())
            f.assertStatus(AccountBalanceStatus.READY)
        }
    }

    @Test
    fun relevantAccountEventsInvalidateBalance() {
        val events = listOf(
            AccountStreamEventType.ACCOUNT_CHANGED,
            AccountStreamEventType.ORDER_CHANGED,
            AccountStreamEventType.CONFIG_CHANGED,
            AccountStreamEventType.SESSION_EXPIRED
        )

        events.forEach { event ->
            AccountBalanceControllerFixture().use { f ->
                f.startReady()

                f.event(event)

                f.assertStatus(AccountBalanceStatus.STALE)
                f.assertNoVisibleBalance()

                assertEquals(3000L, f.scheduler.nextDue)
            }
        }
    }

    @Test
    fun ignoredAndInvalidEventsDoNotRefresh() {
        AccountBalanceControllerFixture().use { f ->
            f.startReady()

            val publications = f.states.size

            f.event(AccountStreamEventType.IGNORED)
            f.event(AccountStreamEventType.INVALID)

            f.assertStatus(AccountBalanceStatus.READY)

            assertEquals(publications, f.states.size)
            assertEquals(10000L, f.scheduler.nextDue)
            assertEquals(1, f.readCalls.get())
        }
    }

    @Test
    fun wrongGenerationCannotInvalidateBalance() {
        AccountBalanceControllerFixture().use { f ->
            f.startReady()

            val publications = f.states.size

            f.invalidate(f.generation + 1L)

            f.event(
                AccountStreamEventType.ACCOUNT_CHANGED,
                expectedGeneration = f.generation - 1L
            )

            f.assertStatus(AccountBalanceStatus.READY)

            assertEquals(publications, f.states.size)
            assertEquals(10000L, f.scheduler.nextDue)
        }
    }

    @Test
    fun expiredBalanceIsNotVisible() {
        AccountBalanceControllerFixture().use { f ->
            f.startReady()

            f.scheduler.moveClockTo(30000L)

            assertNotNull(
                f.controller.snapshot()
                    .freshBalance(f.scheduler.now)
            )

            f.scheduler.moveClockTo(30001L)

            f.assertStatus(AccountBalanceStatus.STALE)
            f.assertNoVisibleBalance()
        }
    }

    @Test
    fun wrongVenueResultIsRejected() {
        AccountBalanceControllerFixture().use { f ->
            f.readAction = {
                f.balance(target = FuturesVenue.REAL)
            }

            f.start()
            f.completeRest()

            f.assertStatus(AccountBalanceStatus.RETRY_WAIT)
            f.assertNoVisibleBalance()

            assertNull(f.controller.snapshot().balance)
            assertEquals(1, f.readers.single().closes.get())
            assertEquals(3000L, f.scheduler.nextDue)
        }
    }

    @Test
    fun futureTimestampIsRejected() {
        AccountBalanceControllerFixture().use { f ->
            f.readAction = {
                f.balance(received = f.scheduler.now + 1L)
            }

            f.start()
            f.completeRest()

            f.assertStatus(AccountBalanceStatus.RETRY_WAIT)
            f.assertNoVisibleBalance()
        }
    }

    @Test
    fun timestampBeforeReadStartIsRejected() {
        AccountBalanceControllerFixture().use { f ->
            f.scheduler.moveClockTo(1000L)

            f.readAction = {
                f.balance(received = 999L)
            }

            f.start()
            f.completeRest()

            f.assertStatus(AccountBalanceStatus.RETRY_WAIT)
            f.assertNoVisibleBalance()

            assertEquals(4000L, f.scheduler.nextDue)
        }
    }

    @Test
    fun readFailureHidesPreviousBalanceAndRetries() {
        AccountBalanceControllerFixture().use { f ->
            f.startReady()

            f.readAction = {
                throw IllegalStateException("Injected failure")
            }

            f.scheduler.advanceBy(10000L)
            f.completeRest()

            f.assertStatus(AccountBalanceStatus.RETRY_WAIT)
            f.assertNoVisibleBalance()

            assertEquals(1, f.readers.first().closes.get())
            assertEquals(13000L, f.scheduler.nextDue)

            f.readAction = { f.balance() }

            f.scheduler.advanceBy(3000L)
            f.completeRest()

            f.assertStatus(AccountBalanceStatus.READY)

            assertEquals(2, f.factoryCalls.get())
            assertEquals(3, f.readCalls.get())
            assertEquals(23000L, f.scheduler.nextDue)
        }
    }

    @Test
    fun factoryFailureRecoversWithFreshAccess() {
        AccountBalanceControllerFixture().use { f ->
            f.rejectNextFactory()

            f.start()

            f.assertStatus(AccountBalanceStatus.RETRY_WAIT)

            assertEquals(0, f.readCalls.get())
            assertEquals(1, f.factoryCalls.get())
            assertTrue(f.readers.isEmpty())
            assertEquals(3000L, f.scheduler.nextDue)

            f.scheduler.advanceBy(3000L)
            f.completeRest()

            f.assertStatus(AccountBalanceStatus.READY)

            assertEquals(2, f.factoryCalls.get())
            assertEquals(1, f.readCalls.get())
        }
    }

    @Test
    fun rejectedWorkerSubmissionRecovers() {
        AccountBalanceControllerFixture().use { f ->
            f.worker.rejectNextTask()

            f.start()

            f.assertStatus(AccountBalanceStatus.RETRY_WAIT)

            assertEquals(0, f.readCalls.get())
            assertEquals(1, f.readers.single().closes.get())
            assertEquals(3000L, f.scheduler.nextDue)

            f.scheduler.advanceBy(3000L)
            f.completeRest()

            f.assertStatus(AccountBalanceStatus.READY)

            assertEquals(2, f.factoryCalls.get())
            assertEquals(1, f.readCalls.get())
        }
    }

    @Test
    fun stopClearsBalanceAndCancelsSchedule() {
        AccountBalanceControllerFixture().use { f ->
            f.startReady()

            f.controller.stop()

            f.assertStatus(AccountBalanceStatus.STOPPED)
            f.assertNoVisibleBalance()

            assertNull(f.controller.snapshot().balance)
            assertNull(f.controller.snapshot().venue)
            assertEquals(0, f.scheduler.readyCount)
            assertEquals(0, f.scheduler.delayedCount)
            assertEquals(1, f.readers.single().closes.get())

            f.scheduler.advanceBy(60000L)

            assertEquals(1, f.readCalls.get())
        }
    }

    @Test
    fun restartPreservesMinimumInterval() {
        AccountBalanceControllerFixture().use { f ->
            f.startReady()

            val oldGeneration = f.generation

            f.controller.stop()
            f.controller.start()

            assertEquals(oldGeneration + 1L, f.generation)
            assertEquals(3000L, f.scheduler.nextDue)

            f.assertNoVisibleBalance()

            f.scheduler.advanceBy(2999L)

            assertEquals(1, f.readCalls.get())

            f.scheduler.advanceBy(1L)
            f.completeRest()

            f.assertStatus(AccountBalanceStatus.READY)

            assertEquals(2, f.readCalls.get())
            assertEquals(2, f.factoryCalls.get())
        }
    }

    @Test
    fun closePermanentlyDisablesController() {
        AccountBalanceControllerFixture().use { f ->
            f.startReady()

            f.controller.close()
            f.awaitTermination()

            f.assertStatus(AccountBalanceStatus.CLOSED)
            f.assertNoVisibleBalance()

            val publications = f.states.size

            f.controller.start()
            f.controller.stop()
            f.controller.close()

            f.scheduler.advanceBy(60000L)

            f.assertStatus(AccountBalanceStatus.CLOSED)

            assertEquals(publications, f.states.size)
            assertEquals(1, f.readCalls.get())
            assertEquals(1, f.readers.single().closes.get())
            assertEquals(0, f.scheduler.delayedCount)
        }
    }

    @Test
    fun realVenueHonorsBuildVariant() {
        if (BuildConfig.MAINNET_ORDERS) {
            AccountBalanceControllerFixture(
                venue = FuturesVenue.REAL
            ).use { f ->
                f.startReady()

                assertEquals(
                    FuturesVenue.REAL,
                    f.controller.snapshot().balance?.venue
                )
            }
        } else {
            val worker = BalanceTestWorker()
            val scheduler = BalanceTestScheduler()

            try {
                var rejected = false

                try {
                    AccountBalanceController(
                        venue = FuturesVenue.REAL,
                        factory = AccountBalanceFactory {
                            error("Factory must not be called")
                        },
                        scheduler = scheduler,
                        rateLimitGuard = AccountRateLimitGuard(
                            elapsed = { scheduler.now },
                            wallClock = { 0L }
                        ),
                        worker = worker,
                        elapsed = { scheduler.now }
                    ).close()
                } catch (_: IllegalArgumentException) {
                    rejected = true
                }

                assertTrue(rejected)
                assertEquals(0, scheduler.delayedCount)
            } finally {
                worker.shutdownNow()
            }
        }
    }

    @Test
    fun observerCanReadSnapshot() {
        AccountBalanceControllerFixture().use { f ->
            val observations =
                mutableListOf<Pair<AccountBalanceSnapshot, AccountBalanceSnapshot>>()

            f.stateAction = { state ->
                observations.add(
                    state to f.controller.snapshot()
                )
            }

            f.startReady()

            assertTrue(observations.isNotEmpty())
            assertEquals(f.states.size, observations.size)

            observations.forEach { (published, current) ->
                assertEquals(published, current)
            }

            assertEquals(
                AccountBalanceStatus.READY,
                observations.last().first.status
            )
        }
    }

    @Test
    fun observerRejectsAllPublicMutations() {
        AccountBalanceControllerFixture().use { f ->
            val failures = mutableListOf<Throwable?>()
            var attempted = false

            f.stateAction = { state ->
                if (!attempted) {
                    attempted = true

                    val actions = listOf<() -> Unit>(
                        { f.controller.start() },
                        { f.controller.stop() },
                        { f.controller.close() },
                        {
                            f.controller.invalidate(
                                state.generation
                            )
                        },
                        {
                            f.event(
                                AccountStreamEventType.ACCOUNT_CHANGED,
                                state.generation
                            )
                        }
                    )

                    actions.forEach { action ->
                        failures.add(
                            runCatching {
                                action()
                            }.exceptionOrNull()
                        )
                    }
                }
            }

            f.startReady()

            assertTrue(attempted)
            assertEquals(5, failures.size)

            failures.forEach { failure ->
                assertTrue(
                    failure is IllegalStateException
                )
                assertEquals(
                    "Balance observer cannot mutate controller",
                    failure?.message
                )
            }

            assertEquals(1L, f.generation)
            assertEquals(1, f.readCalls.get())
            assertEquals(1, f.factoryCalls.get())
            assertEquals(1, f.scheduler.delayedCount)
            assertEquals(10000L, f.scheduler.nextDue)
            assertFalse(f.worker.isShutdown)
            assertFalse(
                f.controller.snapshot().invalidated
            )
        }
    }

    @Test
    fun observerExceptionDoesNotInterruptMonitoring() {
        AccountBalanceControllerFixture().use { f ->
            var callbacks = 0

            f.stateAction = {
                callbacks++

                throw IllegalStateException(
                    "Injected observer failure"
                )
            }

            f.startReady()

            assertTrue(callbacks > 0)
            assertEquals(f.states.size, callbacks)
            assertEquals(1, f.readCalls.get())
            assertEquals(10000L, f.scheduler.nextDue)

            val previousCallbacks = callbacks

            f.scheduler.advanceBy(10000L)
            f.completeRest()

            f.assertStatus(AccountBalanceStatus.READY)

            assertTrue(callbacks > previousCallbacks)
            assertEquals(f.states.size, callbacks)
            assertEquals(2, f.readCalls.get())
            assertEquals(20000L, f.scheduler.nextDue)
        }
    }

    @Test
    fun lifecycleRecoversAfterObserverFailure() {
        AccountBalanceControllerFixture().use { f ->
            var attempted = false

            f.stateAction = {
                if (!attempted) {
                    attempted = true

                    f.controller.stop()
                }
            }

            f.startReady()

            assertTrue(attempted)

            f.stateAction = {}

            val previousGeneration = f.generation

            f.invalidate()

            f.assertStatus(AccountBalanceStatus.STALE)
            f.assertNoVisibleBalance()

            f.controller.stop()

            f.assertStatus(AccountBalanceStatus.STOPPED)
            f.assertNoVisibleBalance()

            assertEquals(0, f.scheduler.delayedCount)

            f.controller.start()

            assertEquals(
                previousGeneration + 1L,
                f.generation
            )

            f.scheduler.advanceBy(3000L)
            f.completeRest()

            f.assertStatus(AccountBalanceStatus.READY)

            assertEquals(2, f.readCalls.get())
            assertEquals(2, f.factoryCalls.get())

            f.controller.close()
            f.awaitTermination()

            f.assertStatus(AccountBalanceStatus.CLOSED)
            f.assertNoVisibleBalance()
        }
    }

    @Test
    fun terminalObserverRejectsAllPublicMutations() {
        AccountBalanceControllerFixture().use { f ->
            val failures = mutableListOf<Throwable?>()
            var terminalCallbacks = 0
            var terminalSnapshot: AccountBalanceSnapshot? = null

            f.startReady()

            f.stateAction = { state ->
                if (state.status == AccountBalanceStatus.CLOSED) {
                    terminalCallbacks++
                    terminalSnapshot = f.controller.snapshot()

                    val actions = listOf<() -> Unit>(
                        { f.controller.start() },
                        { f.controller.stop() },
                        { f.controller.close() },
                        {
                            f.controller.invalidate(
                                state.generation
                            )
                        },
                        {
                            f.event(
                                AccountStreamEventType.ACCOUNT_CHANGED,
                                state.generation
                            )
                        }
                    )

                    actions.forEach { action ->
                        failures.add(
                            runCatching {
                                action()
                            }.exceptionOrNull()
                        )
                    }
                }
            }

            f.controller.close()
            f.awaitTermination()

            assertEquals(1, terminalCallbacks)
            assertEquals(5, failures.size)

            failures.forEach { failure ->
                assertTrue(
                    failure is IllegalStateException
                )
                assertEquals(
                    "Balance observer cannot mutate controller",
                    failure?.message
                )
            }

            assertEquals(
                AccountBalanceStatus.CLOSED,
                terminalSnapshot?.status
            )

            assertNull(terminalSnapshot?.balance)
            assertTrue(f.worker.isShutdown)

            val publications = f.states.size

            f.controller.start()
            f.controller.stop()
            f.controller.close()

            f.scheduler.advanceBy(60000L)

            f.assertStatus(AccountBalanceStatus.CLOSED)
            f.assertNoVisibleBalance()

            assertEquals(1, terminalCallbacks)
            assertEquals(publications, f.states.size)
            assertEquals(1, f.readCalls.get())
            assertEquals(0, f.scheduler.delayedCount)
            assertEquals(0, f.scheduler.readyCount)
            assertEquals(1, f.readers.single().closes.get())
        }
    }

    @Test
    fun terminalObserverExceptionDoesNotPreventCleanup() {
        AccountBalanceControllerFixture().use { f ->
            var terminalCallbacks = 0

            f.startReady()

            f.stateAction = { state ->
                if (state.status == AccountBalanceStatus.CLOSED) {
                    terminalCallbacks++

                    throw IllegalStateException(
                        "Injected terminal observer failure"
                    )
                }
            }

            f.controller.close()
            f.awaitTermination()

            f.assertStatus(AccountBalanceStatus.CLOSED)
            f.assertNoVisibleBalance()

            assertEquals(1, terminalCallbacks)
            assertTrue(f.worker.isShutdown)
            assertTrue(f.worker.isTerminated)
            assertEquals(0, f.scheduler.readyCount)
            assertEquals(0, f.scheduler.delayedCount)
            assertEquals(1, f.readers.single().closes.get())

            val publications = f.states.size

            f.controller.close()
            f.controller.start()

            assertEquals(1, terminalCallbacks)
            assertEquals(publications, f.states.size)
        }
    }

    @Test
    fun publicMutationsRejectWrongThread() {
        AccountBalanceControllerFixture().use { f ->
            f.startReady()

            val generation = f.generation
            val publications = f.states.size

            val failures = f.worker.submit<List<Throwable?>> {
                val actions = listOf<() -> Unit>(
                    { f.controller.start() },
                    { f.controller.stop() },
                    { f.controller.close() },
                    {
                        f.controller.invalidate(generation)
                    },
                    {
                        f.event(
                            AccountStreamEventType.ACCOUNT_CHANGED,
                            generation
                        )
                    }
                )

                actions.map { action ->
                    runCatching {
                        action()
                    }.exceptionOrNull()
                }
            }.get(
                5L,
                java.util.concurrent.TimeUnit.SECONDS
            )

            assertEquals(5, failures.size)

            failures.forEach { failure ->
                assertTrue(
                    failure is IllegalStateException
                )
                assertEquals(
                    "Invalid balance controller thread",
                    failure?.message
                )
            }

            f.assertStatus(AccountBalanceStatus.READY)

            assertEquals(generation, f.generation)
            assertEquals(publications, f.states.size)
            assertEquals(1, f.readCalls.get())
            assertEquals(10000L, f.scheduler.nextDue)
            assertFalse(f.worker.isShutdown)

            f.controller.stop()

            f.assertStatus(AccountBalanceStatus.STOPPED)
            f.assertNoVisibleBalance()
        }
    }

    @Test
    fun deferredObserverMutationRunsAfterCallbackReturns() {
        AccountBalanceControllerFixture().use { f ->
            val externalQueue =
                java.util.ArrayDeque<() -> Unit>()

            var queued = false
            var executed = false
            var insideObserver = false
            var executedInsideObserver = false

            f.stateAction = { state ->
                insideObserver = true

                try {
                    if (
                        state.status == AccountBalanceStatus.READY &&
                        !queued
                    ) {
                        queued = true

                        // The controller owns its scheduler.
                        // Lifecycle dispatch uses a separate queue.
                        externalQueue.addLast {
                            executedInsideObserver = insideObserver

                            f.controller.stop()

                            executed = true
                        }
                    }
                } finally {
                    insideObserver = false
                }
            }

            f.startReady()

            assertTrue(queued)
            assertFalse(executed)
            assertFalse(insideObserver)
            assertEquals(1, externalQueue.size)

            f.assertStatus(AccountBalanceStatus.READY)

            assertEquals(1, f.readCalls.get())
            assertEquals(1, f.scheduler.delayedCount)
            assertEquals(10000L, f.scheduler.nextDue)

            val deferred = externalQueue.removeFirst()

            deferred()

            assertTrue(executed)
            assertFalse(executedInsideObserver)
            assertTrue(externalQueue.isEmpty())

            f.assertStatus(AccountBalanceStatus.STOPPED)
            f.assertNoVisibleBalance()

            assertEquals(1, f.readCalls.get())
            assertEquals(1, f.readers.single().closes.get())
            assertEquals(0, f.scheduler.readyCount)
            assertEquals(0, f.scheduler.delayedCount)

            f.scheduler.advanceBy(60000L)

            assertEquals(1, f.readCalls.get())
            assertTrue(externalQueue.isEmpty())
        }
    }

    @Test
    fun stopDuringRestRejectsLateBalance() {
        AccountBalanceControllerFixture().use { f ->
            f.readAction = {
                f.gate.block()

                f.balance(wallet = "999.00")
            }

            f.start()
            f.gate.awaitEntered()

            assertEquals(1, f.readCalls.get())
            assertTrue(
                f.controller.snapshot().requestPending
            )

            f.controller.stop()

            f.assertStatus(AccountBalanceStatus.STOPPED)
            f.assertNoVisibleBalance()

            val publications = f.states.size
            val posts = f.scheduler.posts.get()

            assertEquals(1, f.readers.single().closes.get())

            f.gate.release()
            f.completeRest()

            f.assertStatus(AccountBalanceStatus.STOPPED)
            f.assertNoVisibleBalance()

            assertNull(f.controller.snapshot().balance)
            assertEquals(publications, f.states.size)
            assertEquals(posts, f.scheduler.posts.get())
            assertEquals(0, f.scheduler.readyCount)
            assertEquals(0, f.scheduler.delayedCount)

            f.scheduler.advanceBy(60000L)

            assertEquals(1, f.readCalls.get())
        }
    }

    @Test
    fun closeDuringRestRejectsLateBalance() {
        AccountBalanceControllerFixture().use { f ->
            f.readAction = {
                f.gate.block()

                f.balance(wallet = "999.00")
            }

            f.start()
            f.gate.awaitEntered()

            f.controller.close()

            f.assertStatus(AccountBalanceStatus.CLOSED)
            f.assertNoVisibleBalance()

            val publications = f.states.size
            val posts = f.scheduler.posts.get()

            assertTrue(f.worker.isShutdown)
            assertEquals(1, f.readers.single().closes.get())

            f.gate.release()
            f.awaitTermination()

            f.scheduler.drainReady()

            f.assertStatus(AccountBalanceStatus.CLOSED)
            f.assertNoVisibleBalance()

            assertNull(f.controller.snapshot().balance)
            assertEquals(publications, f.states.size)
            assertEquals(posts, f.scheduler.posts.get())
            assertEquals(0, f.scheduler.readyCount)
            assertEquals(0, f.scheduler.delayedCount)

            f.controller.start()
            f.scheduler.advanceBy(60000L)

            assertEquals(1, f.readCalls.get())
            assertTrue(f.worker.isTerminated)
        }
    }

    @Test
    fun restartRejectsLateResultFromPreviousGeneration() {
        AccountBalanceControllerFixture().use { f ->
            f.readAction = {
                f.gate.block()

                f.balance(wallet = "999.00")
            }

            f.start()
            f.gate.awaitEntered()

            val oldGeneration = f.generation

            f.controller.stop()

            f.readAction = {
                f.balance(wallet = "120.00")
            }

            f.controller.start()

            assertEquals(
                oldGeneration + 1L,
                f.generation
            )

            f.assertNoVisibleBalance()

            val publications = f.states.size
            val posts = f.scheduler.posts.get()

            f.gate.release()
            f.completeRest()

            assertEquals(publications, f.states.size)
            assertEquals(posts, f.scheduler.posts.get())

            f.assertStatus(AccountBalanceStatus.LOADING)
            f.assertNoVisibleBalance()

            assertEquals(3000L, f.scheduler.nextDue)

            f.scheduler.advanceBy(3000L)
            f.completeRest()

            f.assertStatus(AccountBalanceStatus.READY)

            assertEquals(
                BigDecimal("120.00"),
                f.controller.snapshot().balance?.wallet
            )

            assertEquals(2, f.readCalls.get())
            assertEquals(2, f.factoryCalls.get())
            assertEquals(1, f.readers.first().closes.get())
            assertFalse(f.readers.last().closed.get())
        }
    }

    @Test
    fun cancelledTimerCannotStartReadInNewSession() {
        AccountBalanceControllerFixture().use { f ->
            f.controller.start()

            val oldTimer =
                f.scheduler.captureNextDelayed()

            val oldGeneration = f.generation

            f.controller.stop()
            f.controller.start()

            val publications = f.states.size
            val delayedPosts =
                f.scheduler.delayedPosts.get()

            oldTimer()

            assertEquals(
                oldGeneration + 1L,
                f.generation
            )

            assertEquals(publications, f.states.size)
            assertEquals(0, f.factoryCalls.get())
            assertEquals(0, f.readCalls.get())
            assertEquals(1, f.scheduler.delayedCount)

            assertEquals(
                delayedPosts,
                f.scheduler.delayedPosts.get()
            )

            f.scheduler.runDue()
            f.completeRest()

            f.assertStatus(AccountBalanceStatus.READY)

            assertEquals(1, f.readCalls.get())
            assertEquals(1, f.factoryCalls.get())
        }
    }

    @Test
    fun escapedCompletionCannotOverwriteNewSession() {
        AccountBalanceControllerFixture().use { f ->
            f.readAction = {
                f.balance(wallet = "999.00")
            }

            f.start()
            f.worker.awaitIdle()

            assertEquals(1, f.scheduler.readyCount)

            val oldCompletion =
                f.scheduler.captureNextReady()

            val oldGeneration = f.generation

            f.controller.stop()

            assertEquals(0, f.scheduler.readyCount)

            f.readAction = {
                f.balance(wallet = "130.00")
            }

            f.controller.start()

            f.scheduler.advanceBy(3000L)
            f.completeRest()

            f.assertStatus(AccountBalanceStatus.READY)

            assertEquals(
                oldGeneration + 1L,
                f.generation
            )

            val snapshot = f.controller.snapshot()
            val publications = f.states.size
            val deadline = f.scheduler.nextDue

            oldCompletion()

            assertEquals(
                snapshot,
                f.controller.snapshot()
            )

            assertEquals(
                BigDecimal("130.00"),
                f.controller.snapshot().balance?.wallet
            )

            assertEquals(publications, f.states.size)
            assertEquals(deadline, f.scheduler.nextDue)
            assertEquals(2, f.readCalls.get())
            assertEquals(2, f.factoryCalls.get())
        }
    }

    @Test
    fun previousGenerationEventsCannotInvalidateNewBalance() {
        AccountBalanceControllerFixture().use { f ->
            f.startReady()

            val oldGeneration = f.generation

            f.controller.stop()
            f.controller.start()

            f.scheduler.advanceBy(3000L)
            f.completeRest()

            f.assertStatus(AccountBalanceStatus.READY)

            val snapshot = f.controller.snapshot()
            val publications = f.states.size
            val deadline = f.scheduler.nextDue

            f.invalidate(oldGeneration)

            val events = listOf(
                AccountStreamEventType.ACCOUNT_CHANGED,
                AccountStreamEventType.ORDER_CHANGED,
                AccountStreamEventType.CONFIG_CHANGED,
                AccountStreamEventType.SESSION_EXPIRED
            )

            events.forEach { event ->
                f.event(
                    event,
                    expectedGeneration = oldGeneration
                )
            }

            assertEquals(
                snapshot,
                f.controller.snapshot()
            )

            assertEquals(publications, f.states.size)
            assertEquals(deadline, f.scheduler.nextDue)
            assertEquals(2, f.readCalls.get())

            f.assertStatus(AccountBalanceStatus.READY)
        }
    }

    @Test
    fun invalidationDuringRestSchedulesFollowUpRead() {
        AccountBalanceControllerFixture().use { f ->
            f.readAction = {
                f.gate.block()

                f.balance(wallet = "100.00")
            }

            f.start()
            f.gate.awaitEntered()

            f.event(
                AccountStreamEventType.ACCOUNT_CHANGED
            )

            f.assertNoVisibleBalance()

            assertTrue(
                f.controller.snapshot().requestPending
            )

            assertEquals(0, f.scheduler.delayedCount)
            assertEquals(1, f.readCalls.get())

            f.gate.release()
            f.completeRest()

            f.assertStatus(AccountBalanceStatus.STALE)
            f.assertNoVisibleBalance()

            assertTrue(
                f.controller.snapshot().invalidated
            )

            assertFalse(
                f.controller.snapshot().requestPending
            )

            assertEquals(1, f.readCalls.get())
            assertEquals(3000L, f.scheduler.nextDue)

            f.readAction = {
                f.balance(wallet = "140.00")
            }

            f.scheduler.advanceBy(2999L)

            assertEquals(1, f.readCalls.get())

            f.scheduler.advanceBy(1L)
            f.completeRest()

            f.assertStatus(AccountBalanceStatus.READY)

            assertEquals(
                BigDecimal("140.00"),
                f.controller.snapshot().balance?.wallet
            )

            assertFalse(
                f.controller.snapshot().invalidated
            )

            assertEquals(2, f.readCalls.get())
            assertEquals(1, f.factoryCalls.get())
        }
    }

    @Test
    fun cancelledRestPreservesMinimumReadInterval() {
        AccountBalanceControllerFixture().use { f ->
            f.scheduler.moveClockTo(1000L)

            f.readAction = {
                f.gate.block()

                f.balance(wallet = "999.00")
            }

            f.start()
            f.gate.awaitEntered()

            f.scheduler.moveClockTo(1500L)

            f.controller.stop()

            f.readAction = {
                f.balance(wallet = "150.00")
            }

            f.controller.start()

            assertEquals(4000L, f.scheduler.nextDue)

            f.gate.release()
            f.completeRest()

            f.assertNoVisibleBalance()

            f.scheduler.advanceBy(2499L)

            assertEquals(1, f.readCalls.get())
            assertEquals(1, f.factoryCalls.get())

            f.scheduler.advanceBy(1L)
            f.completeRest()

            f.assertStatus(AccountBalanceStatus.READY)

            assertEquals(
                BigDecimal("150.00"),
                f.controller.snapshot().balance?.wallet
            )

            assertEquals(2, f.readCalls.get())
            assertEquals(2, f.factoryCalls.get())
            assertEquals(14000L, f.scheduler.nextDue)
        }
    }

    @Test
    fun activeCooldownPreventsInitialRest() {
        AccountBalanceControllerFixture().use { f ->
            f.guard.blockFor(120000L)

            f.start()

            f.assertStatus(AccountBalanceStatus.LOADING)
            f.assertNoVisibleBalance()

            assertEquals(0, f.factoryCalls.get())
            assertEquals(0, f.readCalls.get())
            assertEquals(60000L, f.scheduler.nextDue)

            f.scheduler.advanceBy(60000L)

            assertEquals(0, f.readCalls.get())
            assertEquals(0, f.factoryCalls.get())
            assertEquals(60000L, f.guard.remainingMs())
            assertEquals(120000L, f.scheduler.nextDue)

            f.scheduler.advanceBy(59999L)

            assertEquals(0, f.factoryCalls.get())
            assertEquals(0, f.readCalls.get())
            assertEquals(1L, f.guard.remainingMs())

            f.scheduler.advanceBy(1L)
            f.completeRest()

            f.assertStatus(AccountBalanceStatus.READY)

            assertEquals(1, f.factoryCalls.get())
            assertEquals(1, f.readCalls.get())
            assertEquals(0L, f.guard.remainingMs())
            assertEquals(130000L, f.scheduler.nextDue)
        }
    }

    @Test
    fun cooldownAddedBeforeRefreshPreventsRest() {
        AccountBalanceControllerFixture().use { f ->
            f.startReady()

            f.scheduler.advanceBy(5000L)
            f.guard.blockFor(120000L)

            f.scheduler.advanceBy(5000L)

            assertEquals(1, f.readCalls.get())
            assertEquals(1, f.factoryCalls.get())
            assertEquals(70000L, f.scheduler.nextDue)

            f.scheduler.advanceBy(114999L)

            assertEquals(1, f.readCalls.get())
            assertEquals(1L, f.guard.remainingMs())

            f.scheduler.advanceBy(1L)
            f.completeRest()

            f.assertStatus(AccountBalanceStatus.READY)

            assertEquals(2, f.readCalls.get())
            assertEquals(1, f.factoryCalls.get())
            assertEquals(135000L, f.scheduler.nextDue)
        }
    }

    @Test
    fun restRateLimit120SecondsDelaysRetry() {
        AccountBalanceControllerFixture().use { f ->
            f.startReady()

            f.readAction = {
                throw AccountBalanceError(
                    explanation = "Mock rate limit",
                    retryAfterMs = 120000L
                )
            }

            f.scheduler.advanceBy(10000L)
            f.completeRest()

            f.assertStatus(AccountBalanceStatus.RETRY_WAIT)
            f.assertNoVisibleBalance()

            assertEquals(120000L, f.guard.remainingMs())
            assertEquals(70000L, f.scheduler.nextDue)
            assertEquals(2, f.readCalls.get())
            assertEquals(1, f.readers.single().closes.get())

            f.readAction = { f.balance() }

            f.scheduler.advanceBy(119999L)

            assertEquals(2, f.readCalls.get())
            assertEquals(1L, f.guard.remainingMs())

            f.scheduler.advanceBy(1L)
            f.completeRest()

            f.assertStatus(AccountBalanceStatus.READY)

            assertEquals(3, f.readCalls.get())
            assertEquals(2, f.factoryCalls.get())
            assertEquals(0L, f.guard.remainingMs())
            assertEquals(140000L, f.scheduler.nextDue)
        }
    }

    @Test
    fun restRateLimit300SecondsDelaysRetry() {
        AccountBalanceControllerFixture().use { f ->
            f.readAction = {
                throw AccountBalanceError(
                    explanation = "Mock extended cooldown",
                    retryAfterMs = 300000L
                )
            }

            f.start()
            f.completeRest()

            f.assertStatus(AccountBalanceStatus.RETRY_WAIT)
            f.assertNoVisibleBalance()

            assertEquals(300000L, f.guard.remainingMs())
            assertEquals(60000L, f.scheduler.nextDue)
            assertEquals(1, f.readCalls.get())

            f.readAction = { f.balance() }

            f.scheduler.advanceBy(299999L)

            assertEquals(1, f.readCalls.get())
            assertEquals(1L, f.guard.remainingMs())

            f.scheduler.advanceBy(1L)
            f.completeRest()

            f.assertStatus(AccountBalanceStatus.READY)

            assertEquals(2, f.readCalls.get())
            assertEquals(2, f.factoryCalls.get())
            assertEquals(310000L, f.scheduler.nextDue)
        }
    }

    @Test
    fun stopAndRestartCannotBypassBalanceCooldown() {
        AccountBalanceControllerFixture().use { f ->
            f.readAction = {
                throw AccountBalanceError(
                    explanation = "Mock rate limit",
                    retryAfterMs = 120000L
                )
            }

            f.start()
            f.completeRest()

            f.assertStatus(AccountBalanceStatus.RETRY_WAIT)

            val previousGeneration = f.generation

            f.controller.stop()

            f.assertStatus(AccountBalanceStatus.STOPPED)
            f.assertNoVisibleBalance()

            assertEquals(120000L, f.guard.remainingMs())
            assertEquals(0, f.scheduler.delayedCount)

            f.scheduler.advanceBy(1000L)

            f.readAction = { f.balance() }
            f.controller.start()

            assertEquals(previousGeneration + 1L, f.generation)
            assertEquals(119000L, f.guard.remainingMs())
            assertEquals(61000L, f.scheduler.nextDue)

            f.scheduler.advanceBy(118999L)

            assertEquals(1, f.readCalls.get())
            assertEquals(1, f.factoryCalls.get())

            f.scheduler.advanceBy(1L)
            f.completeRest()

            f.assertStatus(AccountBalanceStatus.READY)

            assertEquals(2, f.readCalls.get())
            assertEquals(2, f.factoryCalls.get())
        }
    }

    @Test
    fun lateRateLimitFromStoppedSessionPreservesCooldown() {
        AccountBalanceControllerFixture().use { f ->
            f.readAction = {
                f.gate.block()

                throw AccountBalanceError(
                    explanation = "Mock late rate limit",
                    retryAfterMs = 300000L
                )
            }

            f.start()
            f.gate.awaitEntered()

            val previousGeneration = f.generation

            f.controller.stop()

            f.assertStatus(AccountBalanceStatus.STOPPED)

            val publications = f.states.size
            val posts = f.scheduler.posts.get()

            f.gate.release()
            f.completeRest()

            f.assertStatus(AccountBalanceStatus.STOPPED)
            f.assertNoVisibleBalance()

            assertEquals(300000L, f.guard.remainingMs())
            assertEquals(publications, f.states.size)
            assertEquals(posts, f.scheduler.posts.get())
            assertEquals(0, f.scheduler.readyCount)
            assertEquals(0, f.scheduler.delayedCount)

            f.readAction = { f.balance() }
            f.controller.start()

            assertEquals(previousGeneration + 1L, f.generation)
            assertEquals(60000L, f.scheduler.nextDue)

            f.scheduler.advanceBy(299999L)

            assertEquals(1, f.readCalls.get())

            f.scheduler.advanceBy(1L)
            f.completeRest()

            f.assertStatus(AccountBalanceStatus.READY)

            assertEquals(2, f.readCalls.get())
            assertEquals(2, f.factoryCalls.get())
        }
    }

    @Test
    fun accountEventsCannotBypassActiveCooldown() {
        AccountBalanceControllerFixture().use { f ->
            f.startReady()

            f.guard.blockFor(120000L)

            repeat(100) {
                f.event(AccountStreamEventType.ACCOUNT_CHANGED)
                f.event(AccountStreamEventType.ORDER_CHANGED)
            }

            f.assertNoVisibleBalance()

            assertEquals(1, f.readCalls.get())
            assertEquals(1, f.scheduler.delayedCount)

            f.scheduler.advanceBy(10000L)

            assertEquals(1, f.readCalls.get())
            assertEquals(70000L, f.scheduler.nextDue)

            f.scheduler.advanceBy(109999L)

            assertEquals(1, f.readCalls.get())
            assertEquals(1L, f.guard.remainingMs())

            f.scheduler.advanceBy(1L)
            f.completeRest()

            f.assertStatus(AccountBalanceStatus.READY)

            assertEquals(2, f.readCalls.get())
            assertEquals(1, f.factoryCalls.get())
        }
    }

    @Test
    fun shorterCooldownCannotReduceBalanceReadDeadline() {
        AccountBalanceControllerFixture().use { f ->
            f.guard.blockFor(300000L)

            f.start()

            assertEquals(60000L, f.scheduler.nextDue)

            f.scheduler.advanceBy(10000L)

            f.guard.blockFor(120000L)

            assertEquals(290000L, f.guard.remainingMs())
            assertEquals(60000L, f.scheduler.nextDue)

            f.scheduler.advanceBy(289999L)

            assertEquals(0, f.readCalls.get())
            assertEquals(0, f.factoryCalls.get())
            assertEquals(1L, f.guard.remainingMs())

            f.scheduler.advanceBy(1L)
            f.completeRest()

            f.assertStatus(AccountBalanceStatus.READY)

            assertEquals(1, f.readCalls.get())
            assertEquals(1, f.factoryCalls.get())
            assertEquals(0L, f.guard.remainingMs())
            assertEquals(310000L, f.scheduler.nextDue)
        }
    }
}
