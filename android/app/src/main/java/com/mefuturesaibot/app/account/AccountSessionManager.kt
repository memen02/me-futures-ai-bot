package com.mefuturesaibot.app.account

import com.mefuturesaibot.app.exchange.Scheduler
import android.os.SystemClock
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Display-only account session lifecycle.
 *
 * All lifecycle methods, resource mutations and
 * socket callbacks use one serial controller thread.
 *
 * Blocking REST work uses a dedicated worker.
 * The manager owns its worker and scheduler.
 * Worker results are posted asynchronously.
 * No order execution or automatic venue fallback.
 */
internal class AccountSessionManager(
    private val keyFactory: AccountSessionKeyFactory,
    private val socketFactory: AccountSessionSocketFactory,
    private val scheduler: Scheduler,
    private val rateLimitGuard: AccountRateLimitGuard,
    private val onState: (AccountSessionSnapshot) -> Unit = {},
    private val onEvent: (AccountStreamEvent) -> Unit = {},
    private val worker: ExecutorService =
        ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue<Runnable>(1),
            ThreadPoolExecutor.AbortPolicy()
        ),
    private val elapsed: () -> Long =
        { SystemClock.elapsedRealtime() },
    private val jitterPercent: () -> Int =
        { Random.nextInt(0, 31) }
) : AutoCloseable {

    private val machine = AccountSessionStateMachine()

    private val publicationLock = Any()

    private var keyAccess: AccountSessionKeyAccess? = null

    private var socket: AccountSessionSocket? = null

    private var listenKey: String? = null

    private var pendingRest: Future<*>? = null

    private var retryAttempt = 0

    private var retryDeadline = 0L

    private var keyRenewedAt = -1L

    private var socketStartedAt = -1L

    private var connectedAt = -1L

    private var maintenanceScheduled = false

    private companion object {
        const val STABLE_CONNECTION_MS = 60000L
    }

    fun snapshot(): AccountSessionSnapshot =
        machine.snapshot()

    fun start() {
        val previous = machine.snapshot()

        val attempt = machine.start()

        if (attempt == null) {
            if (
                previous.state != AccountSessionState.CLOSED &&
                machine.snapshot().state ==
                    AccountSessionState.CLOSED
            ) {
                releaseAttempt()
                worker.shutdownNow()
                publishState()
            }

            return
        }

        retryAttempt = 0
        retryDeadline = 0L

        publishState()

        if (!machine.isActiveAttempt(attempt.generation)) {
            return
        }

        beginAttempt(attempt.generation)
    }

    private fun beginAttempt(generation: Long) {
        if (!machine.isActiveAttempt(generation)) {
            return
        }

        if (!rateLimitGuard.isAllowed()) {
            enterRetry(generation)
            return
        }

        try {
            val access = keyFactory.create()

            keyAccess = access

            acquireKey(generation, access)

        } catch (_: Exception) {
            enterRetry(generation)
        }
    }

    /**
     * Acquire the key off the controller thread.
     *
     * Rate limit metadata is recorded before
     * dispatching the result, including when
     * the original attempt has been stopped.
     */
    private fun acquireKey(
        generation: Long,
        access: AccountSessionKeyAccess
    ) {
        check(pendingRest == null)

        pendingRest = worker.submit {
            var key: String? = null
            var requestedAt = -1L

            try {
                if (
                    !Thread.currentThread().isInterrupted &&
                    machine.isActiveAttempt(generation) &&
                    rateLimitGuard.isAllowed()
                ) {
                    requestedAt = elapsed()
                    key = access.start()
                }

            } catch (error: AccountListenKeyError) {
                rateLimitGuard.record(error)

            } catch (_: Exception) {
                // Never expose raw network errors
                // or session credentials.
            }

            val result = key

            publishRestResult(generation) {
                if (
                    !machine.isActiveAttempt(generation) ||
                    keyAccess !== access
                ) {
                    return@publishRestResult
                }

                pendingRest = null

                if (
                    result == null ||
                    !rateLimitGuard.isAllowed()
                ) {
                    enterRetry(generation)
                } else {
                    acceptKey(
                        generation,
                        result,
                        requestedAt
                    )
                }
            }
        }
    }

    private fun acceptKey(
        generation: Long,
        key: String,
        requestedAt: Long
    ) {
        if (
            !AccountSessionPolicy.isKeyUsable(
                requestedAt,
                elapsed()
            )
        ) {
            enterRetry(generation)
            return
        }

        if (!machine.keyAcquired(generation)) {
            return
        }

        listenKey = key
        keyRenewedAt = requestedAt

        publishState()

        if (!machine.isActiveAttempt(generation)) {
            return
        }

        if (
            !rateLimitGuard.isAllowed() ||
            !AccountSessionPolicy.isKeyUsable(
                keyRenewedAt,
                elapsed()
            )
        ) {
            enterRetry(generation)
            return
        }

        try {
            val created = socketFactory.create(
                listenKey = key,
                onStatus = { status ->
                    handleSocketStatus(
                        generation,
                        status
                    )
                },
                onEvent = { event ->
                    handleSocketEvent(
                        generation,
                        event
                    )
                },
                onFailure = { failure ->
                    handleSocketFailure(
                        generation,
                        failure
                    )
                }
            )

            socket = created
            socketStartedAt = elapsed()

            if (
                !rateLimitGuard.isAllowed() ||
                !sessionUsable()
            ) {
                enterRetry(generation)
                return
            }

            created.connect()

        } catch (_: Exception) {
            enterRetry(generation)
        }
    }

    private fun handleSocketStatus(
        generation: Long,
        status: AccountSocketStatus
    ) {
        if (!machine.isActiveAttempt(generation)) {
            return
        }

        when (status) {

            AccountSocketStatus.CONNECTING -> Unit

            AccountSocketStatus.CONNECTED -> {
                if (machine.socketConnected(generation)) {
                    connectedAt = elapsed()

                    if (!sessionUsable()) {
                        enterRetry(generation)
                        return
                    }

                    publishState()

                    scheduleMaintenance(generation)
                }
            }

            AccountSocketStatus.DISCONNECTED -> {
                // Transport delivers onFailure after
                // DISCONNECTED. Preserve its cooldown
                // metadata before entering retry.
            }

            AccountSocketStatus.SESSION_EXPIRED -> {
                enterRetry(generation)
            }
        }
    }

    private fun handleSocketEvent(
        generation: Long,
        event: AccountStreamEvent
    ) {
        if (!machine.acceptsAccountEvent(generation)) {
            return
        }

        if (!sessionUsable()) {
            enterRetry(generation)
            return
        }

        when (event.type) {
            AccountStreamEventType.ACCOUNT_CHANGED,
            AccountStreamEventType.ORDER_CHANGED,
            AccountStreamEventType.CONFIG_CHANGED -> {
                runCatching {
                    onEvent(event)
                }
            }

            AccountStreamEventType.SESSION_EXPIRED -> {
                enterRetry(generation)
            }

            AccountStreamEventType.INVALID,
            AccountStreamEventType.IGNORED -> Unit
        }
    }

    private fun sessionUsable(): Boolean {
        val now = elapsed()

        return AccountSessionPolicy.isKeyUsable(
            keyRenewedAt,
            now
        ) && AccountSessionPolicy.isSocketUsable(
            socketStartedAt,
            now
        )
    }

    /**
     * Remaining time without overflowing a deadline.
     * Invalid or expired timestamps fail closed.
     */
    private fun remainingAgeMs(
        startedAt: Long,
        maximumAgeMs: Long,
        now: Long
    ): Long {
        if (startedAt < 0L || now < startedAt) {
            return 0L
        }

        val age = now - startedAt

        return if (age >= maximumAgeMs) {
            0L
        } else {
            maximumAgeMs - age
        }
    }

    private fun resetBackoffIfStable(now: Long) {
        if (
            connectedAt >= 0L &&
            now >= connectedAt &&
            now - connectedAt >= STABLE_CONNECTION_MS
        ) {
            retryAttempt = 0
        }
    }

    /**
     * Wake at the nearest relevant deadline.
     * Pending REST and cooldown must not cause
     * a zero-delay maintenance loop.
     */
    private fun maintenanceDelayMs(): Long {
        val now = elapsed()

        var delay = minOf(
            AccountSessionPolicy.MAX_SCHEDULE_DELAY_MS,
            remainingAgeMs(
                keyRenewedAt,
                AccountSessionPolicy.KEY_MAX_AGE_MS,
                now
            ),
            remainingAgeMs(
                socketStartedAt,
                AccountSessionPolicy.SOCKET_MAX_AGE_MS,
                now
            )
        )

        if (retryAttempt > 0) {
            val stableRemaining = remainingAgeMs(
                connectedAt,
                STABLE_CONNECTION_MS,
                now
            )

            delay = minOf(delay, stableRemaining)
        }

        if (pendingRest == null) {
            val renewalRemaining = remainingAgeMs(
                keyRenewedAt,
                AccountSessionPolicy.KEEPALIVE_MS,
                now
            )

            val renewalAllowedIn = maxOf(
                renewalRemaining,
                rateLimitGuard.remainingMs()
            )

            delay = minOf(delay, renewalAllowedIn)
        }

        return delay.coerceAtLeast(1L)
    }

    /**
     * Schedule at most one maintenance callback
     * for the currently connected generation.
     */
    private fun scheduleMaintenance(
        generation: Long
    ) {
        if (
            !machine.acceptsAccountEvent(generation) ||
            maintenanceScheduled
        ) {
            return
        }

        if (!sessionUsable()) {
            enterRetry(generation)
            return
        }

        maintenanceScheduled = true

        scheduler.postDelayed(
            maintenanceDelayMs()
        ) {
            if (
                !machine.acceptsAccountEvent(generation)
            ) {
                return@postDelayed
            }

            maintenanceScheduled = false

            maintainSession(generation)
        }
    }

    private fun maintainSession(
        generation: Long
    ) {
        if (!machine.acceptsAccountEvent(generation)) {
            return
        }

        if (!sessionUsable()) {
            enterRetry(generation)
            return
        }

        val now = elapsed()

        resetBackoffIfStable(now)

        val renewalDue =
            now - keyRenewedAt >=
                AccountSessionPolicy.KEEPALIVE_MS

        if (
            renewalDue &&
            pendingRest == null &&
            rateLimitGuard.isAllowed()
        ) {
            val access = keyAccess
            val key = listenKey

            if (access == null || key == null) {
                enterRetry(generation)
                return
            }

            try {
                renewKey(generation, access, key)
            } catch (_: Exception) {
                enterRetry(generation)
                return
            }
        }

        scheduleMaintenance(generation)
    }

    /**
     * Keepalive uses the same bounded REST worker.
     * A changed key requires a fresh socket session.
     */
    private fun renewKey(
        generation: Long,
        access: AccountSessionKeyAccess,
        expectedKey: String
    ) {
        check(pendingRest == null)

        val previousRenewedAt = keyRenewedAt
        val previousSocketStartedAt = socketStartedAt

        pendingRest = worker.submit {
            var renewedKey: String? = null
            var requestedAt = -1L

            try {
                if (
                    !Thread.currentThread().isInterrupted &&
                    machine.acceptsAccountEvent(generation) &&
                    rateLimitGuard.isAllowed()
                ) {
                    requestedAt = elapsed()

                    if (
                        AccountSessionPolicy.isKeyUsable(
                            previousRenewedAt,
                            requestedAt
                        ) &&
                        AccountSessionPolicy.isSocketUsable(
                            previousSocketStartedAt,
                            requestedAt
                        )
                    ) {
                        renewedKey =
                            access.keepAlive(expectedKey)
                    }
                }

            } catch (error: AccountListenKeyError) {
                rateLimitGuard.record(error)

            } catch (_: Exception) {
                // Never expose raw network errors
                // or session credentials.
            }

            val result = renewedKey

            publishRestResult(generation) {
                if (
                    !machine.acceptsAccountEvent(generation) ||
                    keyAccess !== access ||
                    listenKey != expectedKey
                ) {
                    return@publishRestResult
                }

                pendingRest = null

                if (
                    result == null ||
                    result != expectedKey ||
                    !sessionUsable() ||
                    !AccountSessionPolicy.isKeyUsable(
                        requestedAt,
                        elapsed()
                    )
                ) {
                    enterRetry(generation)
                    return@publishRestResult
                }

                keyRenewedAt = requestedAt

                // The previous maintenance deadline
                // belongs to the old renewal timestamp.
                // No other REST operation is pending.
                synchronized(publicationLock) {
                    scheduler.cancelAll()
                }

                maintenanceScheduled = false

                scheduleMaintenance(generation)
            }
        }
    }

    private fun handleSocketFailure(
        generation: Long,
        failure: AccountSocketFailure
    ) {
        if (!machine.isActiveAttempt(generation)) {
            return
        }

        rateLimitGuard.record(failure)

        enterRetry(generation)
    }

    private fun enterRetry(generation: Long) {
        if (!machine.retryRequired(generation)) {
            return
        }

        resetBackoffIfStable(elapsed())

        releaseAttempt()

        val delay = AccountSessionPolicy.retryDelayMs(
            attempt = retryAttempt,
            cooldownRemainingMs =
                rateLimitGuard.remainingMs(),
            jitterPercent = jitterPercent()
        )

        retryAttempt =
            (retryAttempt + 1).coerceAtMost(5)

        val now = elapsed().coerceAtLeast(0L)

        retryDeadline = if (
            delay > Long.MAX_VALUE - now
        ) {
            Long.MAX_VALUE
        } else {
            now + delay
        }

        publishState()

        scheduleRetry(generation)
    }

    private fun isWaitingForRetry(
        generation: Long
    ): Boolean {
        val current = machine.snapshot()

        return current.generation == generation &&
            current.state == AccountSessionState.RETRY_WAIT
    }

    /**
     * Wait using bounded scheduling intervals.
     *
     * Never shorten the server cooldown.
     * Recheck the deadline after every wakeup.
     */
    private fun scheduleRetry(generation: Long) {
        if (!isWaitingForRetry(generation)) {
            return
        }

        val now = elapsed().coerceAtLeast(0L)

        val deadlineRemaining =
            if (retryDeadline <= now) {
                0L
            } else {
                retryDeadline - now
            }

        val remaining = maxOf(
            deadlineRemaining,
            rateLimitGuard.remainingMs()
        )

        if (remaining > 0L) {
            val delay =
                AccountSessionPolicy
                    .scheduleDelayMs(remaining)
                    .coerceAtLeast(1L)

            scheduler.postDelayed(delay) {
                scheduleRetry(generation)
            }

            return
        }

        val attempt = machine.beginRetry(generation)

        if (attempt == null) {
            if (
                machine.snapshot().state ==
                    AccountSessionState.CLOSED
            ) {
                releaseAttempt()
                worker.shutdownNow()
                publishState()
            }

            return
        }

        retryDeadline = 0L

        publishState()

        if (
            machine.isActiveAttempt(
                attempt.generation
            )
        ) {
            beginAttempt(attempt.generation)
        }
    }

    /**
     * Serialize worker publication with cancellation.
     *
     * Never hold this lock during blocking REST,
     * observer callbacks or resource closure.
     *
     * The scheduler must enqueue asynchronously.
     */
    private fun publishRestResult(
        generation: Long,
        action: () -> Unit
    ) {
        synchronized(publicationLock) {
            if (!machine.isActiveAttempt(generation)) {
                return
            }

            scheduler.post(action)
        }
    }

    private fun releaseAttempt() {
        synchronized(publicationLock) {
            scheduler.cancelAll()
        }

        val oldSocket = socket
        val oldKeyAccess = keyAccess
        val oldRest = pendingRest

        socket = null
        keyAccess = null
        listenKey = null
        pendingRest = null

        keyRenewedAt = -1L
        socketStartedAt = -1L
        connectedAt = -1L
        maintenanceScheduled = false

        runCatching {
            oldSocket?.close()
        }

        runCatching {
            oldKeyAccess?.close()
        }

        runCatching {
            oldRest?.cancel(true)
        }

        // Remove cancelled tasks when the supplied
        // executor exposes a purge operation.
        if (worker is ThreadPoolExecutor) {
            worker.purge()
        }
    }

    private fun publishState() {
        val current = machine.snapshot()

        // Observer failures must not interrupt
        // resource cleanup or transport callbacks.
        runCatching {
            onState(current)
        }
    }

    fun stop() {
        if (!machine.stop()) {
            return
        }

        retryAttempt = 0
        retryDeadline = 0L

        releaseAttempt()

        publishState()
    }

    override fun close() {
        if (!machine.close()) {
            return
        }

        retryAttempt = 0
        retryDeadline = 0L

        releaseAttempt()

        worker.shutdownNow()

        publishState()
    }
}
