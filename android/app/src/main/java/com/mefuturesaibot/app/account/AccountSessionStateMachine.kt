package com.mefuturesaibot.app.account

internal enum class AccountSessionState {
    STOPPED,
    ACQUIRING_KEY,
    CONNECTING,
    CONNECTED,
    RETRY_WAIT,
    CLOSED
}

/**
 * Immutable lifecycle snapshot.
 *
 * Generation identifies one connection attempt.
 * It contains no credentials or account data.
 */
internal data class AccountSessionSnapshot(
    val state: AccountSessionState,
    val generation: Long
)

/**
 * Thread-safe lifecycle transitions.
 *
 * Network operations, scheduling and callbacks
 * remain the responsibility of the session manager.
 */
internal class AccountSessionStateMachine {

    private var state =
        AccountSessionState.STOPPED

    private var generation = 0L

    @Synchronized
    fun snapshot(): AccountSessionSnapshot {

        return AccountSessionSnapshot(
            state = state,
            generation = generation
        )
    }

    /**
     * Starts a new monitoring lifecycle.
     * Duplicate starts are rejected.
     */
    @Synchronized
    fun start(): AccountSessionSnapshot? {

        if (state != AccountSessionState.STOPPED) {
            return null
        }

        return beginAttempt()
    }

    /**
     * Accepts a key only for the current attempt.
     */
    @Synchronized
    fun keyAcquired(
        expectedGeneration: Long
    ): Boolean {

        return transition(
            expectedGeneration,
            AccountSessionState.ACQUIRING_KEY,
            AccountSessionState.CONNECTING
        )
    }

    /**
     * Accepts an open socket only after key acquisition.
     */
    @Synchronized
    fun socketConnected(
        expectedGeneration: Long
    ): Boolean {

        return transition(
            expectedGeneration,
            AccountSessionState.CONNECTING,
            AccountSessionState.CONNECTED
        )
    }

    /**
     * Invalidates a failed or expired attempt.
     *
     * Duplicate failure callbacks are rejected.
     */
    @Synchronized
    fun retryRequired(
        expectedGeneration: Long
    ): Boolean {

        if (generation != expectedGeneration) {
            return false
        }

        when (state) {
            AccountSessionState.ACQUIRING_KEY,
            AccountSessionState.CONNECTING,
            AccountSessionState.CONNECTED -> Unit

            else -> return false
        }

        state = AccountSessionState.RETRY_WAIT

        return true
    }

    /**
     * Starts a fresh attempt after the manager
     * has checked its deadline and rate limit guard.
     */
    @Synchronized
    fun beginRetry(
        expectedGeneration: Long
    ): AccountSessionSnapshot? {

        if (
            generation != expectedGeneration ||
            state != AccountSessionState.RETRY_WAIT
        ) {
            return null
        }

        return beginAttempt()
    }

    /**
     * Account events are accepted only from the
     * currently connected attempt.
     */
    @Synchronized
    fun acceptsAccountEvent(
        expectedGeneration: Long
    ): Boolean {

        return generation == expectedGeneration &&
            state == AccountSessionState.CONNECTED
    }

    /**
     * Checks whether a connection attempt remains active.
     */
    @Synchronized
    fun isActiveAttempt(
        expectedGeneration: Long
    ): Boolean {

        if (generation != expectedGeneration) {
            return false
        }

        return when (state) {
            AccountSessionState.ACQUIRING_KEY,
            AccountSessionState.CONNECTING,
            AccountSessionState.CONNECTED -> true

            else -> false
        }
    }

    /**
     * Stops monitoring without permanently
     * closing the state machine.
     */
    @Synchronized
    fun stop(): Boolean {

        if (
            state == AccountSessionState.STOPPED ||
            state == AccountSessionState.CLOSED
        ) {
            return false
        }

        state = AccountSessionState.STOPPED

        return true
    }

    /**
     * Permanently closes this lifecycle.
     */
    @Synchronized
    fun close(): Boolean {

        if (state == AccountSessionState.CLOSED) {
            return false
        }

        state = AccountSessionState.CLOSED

        return true
    }

    private fun beginAttempt(): AccountSessionSnapshot? {

        // Never reuse a generation after overflow.
        if (generation == Long.MAX_VALUE) {

            state = AccountSessionState.CLOSED

            return null
        }

        generation += 1L

        state = AccountSessionState.ACQUIRING_KEY

        return AccountSessionSnapshot(
            state = state,
            generation = generation
        )
    }

    private fun transition(
        expectedGeneration: Long,
        expectedState: AccountSessionState,
        nextState: AccountSessionState
    ): Boolean {

        if (
            generation != expectedGeneration ||
            state != expectedState
        ) {
            return false
        }

        state = nextState

        return true
    }
}
