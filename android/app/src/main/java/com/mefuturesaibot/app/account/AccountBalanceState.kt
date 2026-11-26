package com.mefuturesaibot.app.account

import com.mefuturesaibot.app.demo.FuturesVenue

internal enum class AccountBalanceStatus {
    STOPPED,
    LOADING,
    READY,
    REFRESHING,
    STALE,
    RETRY_WAIT,
    CLOSED
}

/**
 * Display-only balance state.
 *
 * No credentials, order execution or risk sizing.
 * Freshness is evaluated using monotonic time.
 */
internal data class AccountBalanceSnapshot(
    val generation: Long,
    val venue: FuturesVenue?,
    val status: AccountBalanceStatus,
    val balance: AccountBalance?,
    val invalidated: Boolean,
    val requestPending: Boolean
) {
    fun freshBalance(now: Long): AccountBalance? {
        if (
            status != AccountBalanceStatus.READY &&
            status != AccountBalanceStatus.REFRESHING
        ) {
            return null
        }

        if (invalidated) {
            return null
        }

        return balance?.takeIf {
            it.venue == venue && it.fresh(now)
        }
    }
}

/**
 * Identifies one balance request.
 *
 * Generation isolates monitoring sessions.
 * Request ID isolates reads within a session.
 */
internal data class AccountBalanceRequest(
    val generation: Long,
    val requestId: Long,
    val venue: FuturesVenue
)

/**
 * Thread-safe display balance transitions.
 *
 * Scheduling, network calls and observers belong
 * to the balance controller, not this state store.
 */
internal class AccountBalanceStore {

    private var generation = 0L

    private var requestId = 0L

    private var venue: FuturesVenue? = null

    private var status =
        AccountBalanceStatus.STOPPED

    private var balance: AccountBalance? = null

    private var pending: AccountBalanceRequest? = null

    private var invalidated = true

    private var invalidatedDuringRequest = false

    @Synchronized
    fun snapshot(now: Long): AccountBalanceSnapshot {
        val current = balance

        val visibleStatus = if (
            status == AccountBalanceStatus.READY ||
            status == AccountBalanceStatus.REFRESHING
        ) {
            if (
                current == null ||
                invalidated ||
                !current.fresh(now)
            ) {
                AccountBalanceStatus.STALE
            } else {
                status
            }
        } else {
            status
        }

        return AccountBalanceSnapshot(
            generation = generation,
            venue = venue,
            status = visibleStatus,
            balance = current,
            invalidated = invalidated,
            requestPending = pending != null
        )
    }

    /**
     * Start a new isolated monitoring session.
     * Duplicate starts are rejected.
     */
    @Synchronized
    fun start(target: FuturesVenue): Long? {
        if (status != AccountBalanceStatus.STOPPED) {
            return null
        }

        if (generation == Long.MAX_VALUE) {
            close()
            return null
        }

        generation++

        requestId = 0L

        venue = target

        balance = null
        pending = null

        invalidated = true
        invalidatedDuringRequest = false

        status = AccountBalanceStatus.LOADING

        return generation
    }

    @Synchronized
    fun isActive(expectedGeneration: Long): Boolean {
        return generation == expectedGeneration &&
            venue != null &&
            status != AccountBalanceStatus.STOPPED &&
            status != AccountBalanceStatus.CLOSED
    }

    /**
     * Allow only one outstanding REST request.
     */
    @Synchronized
    fun beginRead(
        expectedGeneration: Long
    ): AccountBalanceRequest? {
        if (
            !isActive(expectedGeneration) ||
            pending != null
        ) {
            return null
        }

        val target = venue ?: return null

        if (requestId == Long.MAX_VALUE) {
            close()
            return null
        }

        requestId++

        val request = AccountBalanceRequest(
            generation = generation,
            requestId = requestId,
            venue = target
        )

        pending = request

        invalidatedDuringRequest = false

        status = if (balance == null) {
            AccountBalanceStatus.LOADING
        } else {
            AccountBalanceStatus.REFRESHING
        }

        return request
    }

    @Synchronized
    fun isCurrent(
        request: AccountBalanceRequest
    ): Boolean {
        return isActive(request.generation) &&
            request.venue == venue &&
            pending == request
    }

    /**
     * An account event invalidates the cached data.
     *
     * Events received during a REST request require
     * another synchronization after that request.
     */
    @Synchronized
    fun invalidate(
        expectedGeneration: Long
    ): Boolean {
        if (!isActive(expectedGeneration)) {
            return false
        }

        invalidated = true

        if (pending != null) {
            invalidatedDuringRequest = true
        }

        if (
            balance != null &&
            status != AccountBalanceStatus.RETRY_WAIT
        ) {
            status = AccountBalanceStatus.STALE
        }

        return true
    }

    /**
     * Accept only a current, fresh, venue-safe result.
     */
    @Synchronized
    fun accept(
        request: AccountBalanceRequest,
        result: AccountBalance,
        now: Long
    ): Boolean {
        if (!isCurrent(request)) {
            return false
        }

        if (
            result.venue != request.venue ||
            !result.fresh(now) ||
            (
                balance != null &&
                result.receivedElapsed <
                    balance!!.receivedElapsed
            )
        ) {
            fail(request)
            return false
        }

        pending = null

        balance = result

        invalidated = invalidatedDuringRequest

        invalidatedDuringRequest = false

        status = if (invalidated) {
            AccountBalanceStatus.STALE
        } else {
            AccountBalanceStatus.READY
        }

        return true
    }

    /**
     * Preserve the last snapshot for stale display,
     * but never present it as a fresh balance.
     */
    @Synchronized
    fun fail(
        request: AccountBalanceRequest
    ): Boolean {
        if (!isCurrent(request)) {
            return false
        }

        pending = null

        invalidated = true
        invalidatedDuringRequest = false

        status = AccountBalanceStatus.RETRY_WAIT

        return true
    }

    @Synchronized
    fun stop(): Boolean {
        if (
            status == AccountBalanceStatus.STOPPED ||
            status == AccountBalanceStatus.CLOSED
        ) {
            return false
        }

        clear()

        status = AccountBalanceStatus.STOPPED

        return true
    }

    @Synchronized
    fun close(): Boolean {
        if (status == AccountBalanceStatus.CLOSED) {
            return false
        }

        clear()

        status = AccountBalanceStatus.CLOSED

        return true
    }

    private fun clear() {
        venue = null
        balance = null
        pending = null

        invalidated = true
        invalidatedDuringRequest = false
    }
}
