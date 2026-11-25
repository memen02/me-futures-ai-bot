package com.mefuturesaibot.app.account

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AccountSessionStateMachineTest {

    private val machine = AccountSessionStateMachine()

    private fun start(): Long {
        return requireNotNull(machine.start()).generation
    }

    private fun connect(): Long {
        val generation = start()
        assertTrue(machine.keyAcquired(generation))
        assertTrue(machine.socketConnected(generation))
        return generation
    }

    @Test
    fun initialStateIsStopped() {
        assertEquals(
            AccountSessionSnapshot(
                AccountSessionState.STOPPED,
                0L
            ),
            machine.snapshot()
        )
    }

    @Test
    fun startCreatesFirstGeneration() {
        assertEquals(1L, start())
        assertEquals(
            AccountSessionState.ACQUIRING_KEY,
            machine.snapshot().state
        )
    }

    @Test
    fun duplicateStartIsRejected() {
        val generation = start()
        assertNull(machine.start())
        assertEquals(
            generation,
            machine.snapshot().generation
        )
    }

    @Test
    fun keyAcquisitionMovesToConnecting() {
        val generation = start()
        assertTrue(machine.keyAcquired(generation))
        assertEquals(
            AccountSessionState.CONNECTING,
            machine.snapshot().state
        )
    }

    @Test
    fun duplicateKeyAcquisitionIsRejected() {
        val generation = start()
        assertTrue(machine.keyAcquired(generation))
        assertFalse(machine.keyAcquired(generation))
    }

    @Test
    fun wrongGenerationCannotAcquireKey() {
        val generation = start()
        assertFalse(machine.keyAcquired(generation + 1L))
        assertFalse(machine.keyAcquired(generation - 1L))
        assertEquals(
            AccountSessionState.ACQUIRING_KEY,
            machine.snapshot().state
        )
    }

    @Test
    fun socketCannotConnectBeforeKeyAcquisition() {
        val generation = start()
        assertFalse(machine.socketConnected(generation))
    }

    @Test
    fun socketConnectsAfterKeyAcquisition() {
        val generation = connect()
        assertEquals(
            AccountSessionState.CONNECTED,
            machine.snapshot().state
        )
        assertTrue(machine.acceptsAccountEvent(generation))
    }

    @Test
    fun duplicateSocketConnectedIsRejected() {
        val generation = connect()
        assertFalse(machine.socketConnected(generation))
    }

    @Test
    fun wrongGenerationCannotConnectSocket() {
        val generation = start()
        assertTrue(machine.keyAcquired(generation))
        assertFalse(
            machine.socketConnected(generation + 1L)
        )
        assertEquals(
            AccountSessionState.CONNECTING,
            machine.snapshot().state
        )
    }

    @Test
    fun eventsBeforeConnectionAreRejected() {
        val generation = start()
        assertFalse(machine.acceptsAccountEvent(generation))
        assertTrue(machine.keyAcquired(generation))
        assertFalse(machine.acceptsAccountEvent(generation))
    }

    @Test
    fun connectedSessionRejectsWrongGenerationEvents() {
        val generation = connect()
        assertFalse(
            machine.acceptsAccountEvent(generation - 1L)
        )
        assertFalse(
            machine.acceptsAccountEvent(generation + 1L)
        )
    }

    @Test
    fun failureDuringKeyAcquisitionRequiresRetry() {
        val generation = start()
        assertTrue(machine.retryRequired(generation))
        assertEquals(
            AccountSessionState.RETRY_WAIT,
            machine.snapshot().state
        )
    }

    @Test
    fun failureDuringConnectionRequiresRetry() {
        val generation = start()
        assertTrue(machine.keyAcquired(generation))
        assertTrue(machine.retryRequired(generation))
        assertEquals(
            AccountSessionState.RETRY_WAIT,
            machine.snapshot().state
        )
    }

    @Test
    fun connectedFailureInvalidatesEvents() {
        val generation = connect()
        assertTrue(machine.retryRequired(generation))
        assertFalse(machine.acceptsAccountEvent(generation))
        assertFalse(machine.isActiveAttempt(generation))
    }

    @Test
    fun duplicateFailureIsRejected() {
        val generation = connect()
        assertTrue(machine.retryRequired(generation))
        assertFalse(machine.retryRequired(generation))
    }

    @Test
    fun staleFailureCannotInvalidateCurrentSession() {
        val generation = connect()
        assertFalse(machine.retryRequired(generation - 1L))
        assertTrue(machine.acceptsAccountEvent(generation))
    }

    @Test
    fun retryCreatesNewGeneration() {
        val old = connect()
        assertTrue(machine.retryRequired(old))

        val next = requireNotNull(machine.beginRetry(old))

        assertEquals(old + 1L, next.generation)
        assertEquals(
            AccountSessionState.ACQUIRING_KEY,
            next.state
        )
    }

    @Test
    fun retryRequiresCorrectStateAndGeneration() {
        val generation = connect()
        assertNull(machine.beginRetry(generation))
        assertTrue(machine.retryRequired(generation))
        assertNull(machine.beginRetry(generation + 1L))
        assertEquals(
            AccountSessionState.RETRY_WAIT,
            machine.snapshot().state
        )
    }

    @Test
    fun oldCallbacksCannotModifyNewAttempt() {
        val old = connect()
        assertTrue(machine.retryRequired(old))

        val next = requireNotNull(
            machine.beginRetry(old)
        ).generation

        assertFalse(machine.keyAcquired(old))
        assertFalse(machine.socketConnected(old))
        assertFalse(machine.retryRequired(old))
        assertFalse(machine.acceptsAccountEvent(old))
        assertFalse(machine.isActiveAttempt(old))
        assertNull(machine.beginRetry(old))

        assertTrue(machine.keyAcquired(next))
        assertTrue(machine.socketConnected(next))
        assertTrue(machine.acceptsAccountEvent(next))
    }

    @Test
    fun activeAttemptTracksLifecycle() {
        val generation = start()

        assertTrue(machine.isActiveAttempt(generation))
        assertTrue(machine.keyAcquired(generation))
        assertTrue(machine.isActiveAttempt(generation))
        assertTrue(machine.socketConnected(generation))
        assertTrue(machine.isActiveAttempt(generation))

        assertTrue(machine.retryRequired(generation))
        assertFalse(machine.isActiveAttempt(generation))
    }

    @Test
    fun stopInvalidatesConnectedSession() {
        val generation = connect()

        assertTrue(machine.stop())
        assertFalse(machine.acceptsAccountEvent(generation))
        assertFalse(machine.isActiveAttempt(generation))
        assertFalse(machine.retryRequired(generation))
        assertEquals(
            AccountSessionState.STOPPED,
            machine.snapshot().state
        )
    }

    @Test
    fun stopDuringRetryRejectsScheduledReconnect() {
        val generation = connect()

        assertTrue(machine.retryRequired(generation))
        assertTrue(machine.stop())
        assertNull(machine.beginRetry(generation))
    }

    @Test
    fun duplicateStopIsRejected() {
        assertFalse(machine.stop())
        start()
        assertTrue(machine.stop())
        assertFalse(machine.stop())
    }

    @Test
    fun restartAfterStopUsesNewGeneration() {
        val old = connect()

        assertTrue(machine.stop())

        val next = start()

        assertEquals(old + 1L, next)
        assertFalse(machine.keyAcquired(old))
        assertFalse(machine.socketConnected(old))
        assertFalse(machine.retryRequired(old))
        assertTrue(machine.isActiveAttempt(next))
    }

    @Test
    fun closePermanentlyRejectsOperations() {
        val generation = connect()

        assertTrue(machine.close())
        assertFalse(machine.close())
        assertFalse(machine.stop())
        assertNull(machine.start())
        assertNull(machine.beginRetry(generation))
        assertFalse(machine.keyAcquired(generation))
        assertFalse(machine.socketConnected(generation))
        assertFalse(machine.retryRequired(generation))
        assertFalse(machine.acceptsAccountEvent(generation))
        assertFalse(machine.isActiveAttempt(generation))

        assertEquals(
            AccountSessionState.CLOSED,
            machine.snapshot().state
        )
    }

    @Test
    fun closeWhileStoppedPreventsStart() {
        assertTrue(machine.close())
        assertNull(machine.start())
    }

    @Test
    fun closeDuringRetryRejectsReconnect() {
        val generation = connect()

        assertTrue(machine.retryRequired(generation))
        assertTrue(machine.close())
        assertNull(machine.beginRetry(generation))
    }

    @Test
    fun concurrentStartsAllowOnlyOneAttempt() {
        val workers = Executors.newFixedThreadPool(4)
        val gate = CountDownLatch(1)

        try {
            val jobs = (1..32).map {
                workers.submit<Boolean> {
                    check(gate.await(5L, TimeUnit.SECONDS))
                    machine.start() != null
                }
            }

            gate.countDown()

            val accepted = jobs.count {
                it.get(5L, TimeUnit.SECONDS)
            }

            assertEquals(1, accepted)
            assertEquals(1L, machine.snapshot().generation)
            assertEquals(
                AccountSessionState.ACQUIRING_KEY,
                machine.snapshot().state
            )
        } finally {
            workers.shutdownNow()
        }
    }

    @Test
    fun concurrentRetriesAllowOnlyOneNewAttempt() {
        val generation = connect()

        assertTrue(machine.retryRequired(generation))

        val workers = Executors.newFixedThreadPool(4)
        val gate = CountDownLatch(1)

        try {
            val jobs = (1..32).map {
                workers.submit<Boolean> {
                    check(gate.await(5L, TimeUnit.SECONDS))
                    machine.beginRetry(generation) != null
                }
            }

            gate.countDown()

            val accepted = jobs.count {
                it.get(5L, TimeUnit.SECONDS)
            }

            assertEquals(1, accepted)
            assertEquals(
                generation + 1L,
                machine.snapshot().generation
            )
            assertEquals(
                AccountSessionState.ACQUIRING_KEY,
                machine.snapshot().state
            )
        } finally {
            workers.shutdownNow()
        }
    }
}
