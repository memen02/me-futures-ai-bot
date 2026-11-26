package com.mefuturesaibot.app.account

import android.os.SystemClock
import com.mefuturesaibot.app.BuildConfig
import com.mefuturesaibot.app.demo.FuturesVenue
import com.mefuturesaibot.app.exchange.Scheduler
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * Display-only account balance controller.
 *
 * Lifecycle methods, events and scheduler callbacks
 * must use the thread that constructs this controller.
 *
 * The worker and scheduler are exclusively owned
 * by this controller. Scheduler callbacks must be
 * asynchronous and execute on the controller thread.
 * The worker must execute REST tasks serially and
 * must not execute them inline on the controller thread.
 *
 * State observers are read-only callbacks.
 * They may read snapshot(), but lifecycle mutations
 * must be deferred until the callback has returned.
 *
 * A controller belongs to one immutable venue
 * and credential identity.
 *
 * No order execution or automatic venue fallback.
 */
internal class AccountBalanceController(
    private val venue: FuturesVenue,
    factory: AccountBalanceFactory,
    private val scheduler: Scheduler,
    private val rateLimitGuard: AccountRateLimitGuard,
    onState: (AccountBalanceSnapshot) -> Unit = {},
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

    init {
        require(
            venue != FuturesVenue.REAL ||
                BuildConfig.MAINNET_ORDERS
        )
    }

    private val controllerThread =
        Thread.currentThread()

    private val store = AccountBalanceStore()

    private val publicationLock = Any()

    private var balanceFactory:
        AccountBalanceFactory? = factory

    private var observer:
        ((AccountBalanceSnapshot) -> Unit)? = onState

    private var publishingState = false

    private var access: AccountBalanceAccess? = null

    private var pendingRest: Future<*>? = null

    // Written by the worker before starting a read.
    // Preserved even when a result is cancelled or stale.
    private val lastReadStartedAt = AtomicLong(-1L)

    private var retryAttempt = 0

    private var retryDeadline = 0L

    private var requestedDeadline = Long.MAX_VALUE

    private var scheduledToken: Any? = null

    private var scheduledDeadline = Long.MAX_VALUE

    private fun requireControllerThread() {
        check(
            Thread.currentThread() === controllerThread
        ) {
            "Invalid balance controller thread"
        }
    }

    private fun requireMutationAllowed() {
        requireControllerThread()

        check(!publishingState) {
            "Balance observer cannot mutate controller"
        }
    }

    private fun now(): Long =
        elapsed().coerceAtLeast(0L)

    fun snapshot(): AccountBalanceSnapshot =
        store.snapshot(now())

    fun start() {
        requireMutationAllowed()

        val previous = snapshot()

        val generation = store.start(venue)

        if (generation == null) {
            if (
                previous.status != AccountBalanceStatus.CLOSED &&
                snapshot().status == AccountBalanceStatus.CLOSED
            ) {
                finishClosed()
            }

            return
        }

        requestedDeadline = now()

        publishState()

        if (store.isActive(generation)) {
            scheduleRead(generation)
        }
    }

    /**
     * Invalidate only the intended monitoring session.
     *
     * Callers must preserve the generation captured
     * when their event source was attached.
     */
    fun invalidate(expectedGeneration: Long) {
        requireMutationAllowed()

        if (!store.invalidate(expectedGeneration)) {
            return
        }

        val eventDeadline =
            AccountBalancePolicy.deadline(
                now(),
                AccountBalancePolicy.EVENT_DEBOUNCE_MS
            )

        requestedDeadline = minOf(
            requestedDeadline,
            eventDeadline
        )

        publishState()

        if (store.isActive(expectedGeneration)) {
            scheduleRead(expectedGeneration)
        }
    }

    fun onAccountEvent(
        expectedGeneration: Long,
        event: AccountStreamEvent
    ) {
        requireMutationAllowed()

        when (event.type) {
            AccountStreamEventType.ACCOUNT_CHANGED,
            AccountStreamEventType.ORDER_CHANGED,
            AccountStreamEventType.CONFIG_CHANGED,
            AccountStreamEventType.SESSION_EXPIRED ->
                invalidate(expectedGeneration)

            AccountStreamEventType.IGNORED,
            AccountStreamEventType.INVALID -> Unit
        }
    }

    private fun readDelayMs(): Long {
        val current = now()

        return AccountBalancePolicy.nextReadDelayMs(
            requestedDelayMs =
                AccountBalancePolicy.remainingMs(
                    current,
                    requestedDeadline
                ),
            minimumIntervalDelayMs =
                AccountBalancePolicy.minimumReadDelayMs(
                    lastReadStartedAt.get(),
                    current
                ),
            retryDelayMs =
                AccountBalancePolicy.remainingMs(
                    current,
                    retryDeadline
                ),
            cooldownMs =
                rateLimitGuard.remainingMs()
        )
    }

    private fun scheduleRead(generation: Long) {
        if (!store.isActive(generation)) {
            return
        }

        if (
            pendingRest != null ||
            snapshot().requestPending
        ) {
            return
        }

        val delay =
            AccountBalancePolicy.scheduleDelayMs(
                readDelayMs()
            )

        val deadline =
            AccountBalancePolicy.deadline(
                now(),
                delay
            )

        // Keep an existing wake-up when it is already
        // early enough. Recheck all gates when it runs.
        if (
            scheduledToken != null &&
            scheduledDeadline <= deadline
        ) {
            return
        }

        val token = Any()

        synchronized(publicationLock) {
            scheduler.cancelAll()

            scheduledToken = token
            scheduledDeadline = deadline

            scheduler.postDelayed(delay) {
                requireControllerThread()

                if (
                    scheduledToken !== token ||
                    !store.isActive(generation)
                ) {
                    return@postDelayed
                }

                scheduledToken = null
                scheduledDeadline = Long.MAX_VALUE

                if (readDelayMs() > 0L) {
                    scheduleRead(generation)
                } else {
                    beginRead(generation)
                }
            }
        }
    }

    private fun beginRead(generation: Long) {
        if (
            !store.isActive(generation) ||
            pendingRest != null
        ) {
            return
        }

        if (readDelayMs() > 0L) {
            scheduleRead(generation)
            return
        }

        val request = store.beginRead(generation)

        if (request == null) {
            if (
                snapshot().status ==
                    AccountBalanceStatus.CLOSED
            ) {
                finishClosed()
            }

            return
        }

        requestedDeadline = Long.MAX_VALUE

        publishState()

        if (!store.isCurrent(request)) {
            return
        }

        try {
            val reader = access ?: run {
                val currentFactory =
                    checkNotNull(balanceFactory)

                currentFactory.create().also {
                    access = it
                }
            }

            pendingRest = worker.submit {
                performRead(request, reader)
            }

        } catch (_: Exception) {
            if (store.fail(request)) {
                enterRetry(generation)
            }
        }
    }

    /**
     * Blocking network work.
     *
     * Never publishes raw exception messages,
     * credentials or request URLs.
     */
    private fun performRead(
        request: AccountBalanceRequest,
        reader: AccountBalanceAccess
    ) {
        var result: AccountBalance? = null
        var startedAt = -1L

        try {
            check(
                Thread.currentThread() !== controllerThread
            ) {
                "REST worker must run off controller thread"
            }

            if (
                !Thread.currentThread().isInterrupted &&
                store.isCurrent(request) &&
                rateLimitGuard.isAllowed()
            ) {
                val candidate = now()
                val previous = lastReadStartedAt.get()

                val intervalReady =
                    AccountBalancePolicy.minimumReadDelayMs(
                        previous,
                        candidate
                    ) == 0L

                if (
                    intervalReady &&
                    lastReadStartedAt.compareAndSet(
                        previous,
                        candidate
                    )
                ) {
                    startedAt = candidate

                    // A cancelled read may consume an interval,
                    // but must not bypass the next read gate.
                    if (
                        !Thread.currentThread().isInterrupted &&
                        store.isCurrent(request) &&
                        rateLimitGuard.isAllowed()
                    ) {
                        result = reader.read()
                    }
                }
            }

        } catch (error: AccountBalanceError) {
            // Preserve shared cooldown even if
            // the original session has stopped.
            rateLimitGuard.record(error)

        } catch (_: Exception) {
            // Sanitized failure.
        }

        val completedResult = result
        val actualStart = startedAt

        synchronized(publicationLock) {
            if (!store.isCurrent(request)) {
                return
            }

            scheduler.post {
                completeRead(
                    request,
                    reader,
                    completedResult,
                    actualStart
                )
            }
        }
    }

    private fun completeRead(
        request: AccountBalanceRequest,
        reader: AccountBalanceAccess,
        result: AccountBalance?,
        startedAt: Long
    ) {
        requireControllerThread()

        if (
            !store.isCurrent(request) ||
            access !== reader
        ) {
            return
        }

        pendingRest = null

        val current = now()

        val accepted = if (
            result != null &&
            startedAt >= 0L &&
            result.receivedElapsed >= startedAt
        ) {
            store.accept(
                request,
                result,
                current
            )
        } else {
            store.fail(request)
            false
        }

        if (!accepted) {
            enterRetry(request.generation)
            return
        }

        retryAttempt = 0
        retryDeadline = 0L

        val state = store.snapshot(current)

        val normalDeadline =
            AccountBalancePolicy.deadline(
                current,
                AccountBalancePolicy.NORMAL_REFRESH_MS
            )

        requestedDeadline = minOf(
            requestedDeadline,
            normalDeadline
        )

        if (state.invalidated) {
            requestedDeadline = minOf(
                requestedDeadline,
                AccountBalancePolicy.deadline(
                    current,
                    AccountBalancePolicy.EVENT_DEBOUNCE_MS
                )
            )
        }

        publishState()

        if (store.isActive(request.generation)) {
            scheduleRead(request.generation)
        }
    }

    private fun enterRetry(generation: Long) {
        if (!store.isActive(generation)) {
            return
        }

        releaseAccess()

        val delay =
            AccountBalancePolicy.retryDelayMs(
                retryAttempt,
                jitterPercent()
            )

        retryAttempt = minOf(
            retryAttempt + 1,
            AccountBalancePolicy.MAX_RETRY_ATTEMPT
        )

        retryDeadline =
            AccountBalancePolicy.deadline(
                now(),
                delay
            )

        requestedDeadline = now()

        publishState()

        if (store.isActive(generation)) {
            scheduleRead(generation)
        }
    }

    private fun releaseAccess() {
        val oldAccess = access
        val oldRest = pendingRest

        access = null
        pendingRest = null

        runCatching {
            oldAccess?.close()
        }

        runCatching {
            oldRest?.cancel(true)
        }

        if (worker is ThreadPoolExecutor) {
            worker.purge()
        }
    }

    private fun releaseSession() {
        synchronized(publicationLock) {
            scheduledToken = null
            scheduledDeadline = Long.MAX_VALUE

            scheduler.cancelAll()
        }

        releaseAccess()

        requestedDeadline = Long.MAX_VALUE

        // Preserve retry and minimum-read deadlines
        // across stop/start of this controller.
        // The shared rate-limit guard is never reset.
    }

    private fun notifyObserver(
        callback: (AccountBalanceSnapshot) -> Unit
    ) {
        check(!publishingState)

        publishingState = true

        try {
            runCatching {
                callback(snapshot())
            }
        } finally {
            publishingState = false
        }
    }

    private fun publishState() {
        val callback = observer ?: return

        notifyObserver(callback)
    }

    fun stop() {
        requireMutationAllowed()

        if (!store.stop()) {
            return
        }

        releaseSession()

        publishState()
    }

    private fun finishClosed() {
        releaseSession()

        balanceFactory = null

        worker.shutdownNow()

        val callback = observer

        observer = null

        if (callback != null) {
            notifyObserver(callback)
        }
    }

    override fun close() {
        requireMutationAllowed()

        if (!store.close()) {
            return
        }

        finishClosed()
    }
}
