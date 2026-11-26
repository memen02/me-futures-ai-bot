package com.mefuturesaibot.app.account

import android.os.SystemClock
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import com.mefuturesaibot.app.exchange.Scheduler
import com.mefuturesaibot.app.BuildConfig
import com.mefuturesaibot.app.demo.FuturesVenue
import com.mefuturesaibot.app.exchange.AndroidHandlerScheduler

/**
 * Display-only account monitoring snapshot.
 *
 * Never used for order execution or position sizing.
 * Balance is exposed only for a valid connected session.
 */
internal data class AccountMonitoringSnapshot(
    val venue: FuturesVenue,
    val session: AccountSessionSnapshot,
    val balanceStatus: AccountBalanceStatus,
    val balance: AccountBalance?
)

/** Injectable factories must return fresh resources on the owner thread. */
internal class AccountMonitoringDependencies(
    val balanceFactory: (FuturesVenue, BinanceCredentials) -> AccountBalanceFactory =
        AccountBalanceDependencies::balanceFactory,
    val keyFactory: (FuturesVenue, String) -> AccountSessionKeyFactory =
        AccountSessionDependencies::keyFactory,
    val socketFactory: (FuturesVenue, () -> Scheduler) -> AccountSessionSocketFactory =
        AccountSessionDependencies::socketFactory,
    val schedulerFactory: () -> Scheduler = {
        check(Looper.myLooper() === Looper.getMainLooper()) {
            "Production monitoring requires main thread"
        }
        AndroidHandlerScheduler()
    },
    val workerFactory: () -> ExecutorService = {
        ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            ArrayBlockingQueue<Runnable>(1), ThreadPoolExecutor.AbortPolicy())
    }
)

/**
 * Coordinates one immutable venue and credential
 * revision at a time.
 *
 * All lifecycle methods must run on the main thread
 * when using production Android schedulers.
 *
 * No trading, credential persistence or venue fallback.
 */
internal class AccountMonitoringCoordinator(
    currentRevision:
        (FuturesVenue) -> CredentialRevisionGuard.Revision,
    private val scope: AccountMonitoringRateLimitScope =
        AccountMonitoringRateLimitScope.processWide,
    private val elapsed: () -> Long =
        { SystemClock.elapsedRealtime() },
    private val dependencies: AccountMonitoringDependencies = AccountMonitoringDependencies()
) : AutoCloseable {

    private val owner = Thread.currentThread()

    private var pendingCredentials:
        BinanceCredentials? = null

    private var active: Resources? = null

    private var closed = false

    private val lifecycle = AccountMonitoringLifecycle(
        currentRevision = currentRevision,
        resourceFactory = { token ->
            createResources(
                token,
                checkNotNull(pendingCredentials)
            ).also {
                active = it
            }
        }
    )

    private class Resources(
        val token: AccountSessionIsolation.Token,
        val balance: AccountBalanceController,
        val session: AccountSessionManager
    ) : AutoCloseable {

        var balanceGeneration: Long? = null

        var previousState = AccountSessionState.STOPPED

        // Observer exceptions may be swallowed by the session manager.
        // Never publish a balance after an invalidation callback fails.
        var callbackFailed = false

        override fun close() {
            balanceGeneration = null

            var failure: Exception? = null
            try {
                session.close()
            } catch (error: Exception) {
                failure = error
            }
            try {
                balance.close()
            } catch (error: Exception) {
                val first = failure
                if (first == null) failure = error
                else if (first !== error) first.addSuppressed(error)
            }
            failure?.let { throw it }
        }
    }

    private fun requireOwner() {
        check(Thread.currentThread() === owner) {
            "Invalid monitoring coordinator thread"
        }
    }

    private fun createResources(
        token: AccountSessionIsolation.Token,
        keys: BinanceCredentials
    ): Resources {

        val venue = token.venue

        val balanceScheduler = dependencies.schedulerFactory()
        val balanceFactory = dependencies.balanceFactory(venue, keys)
        val balanceWorker = dependencies.workerFactory()
        val balance = try {
            AccountBalanceController(
                venue = venue,
                factory = balanceFactory,
                scheduler = balanceScheduler,
                rateLimitGuard = scope.guard,
                worker = balanceWorker,
                elapsed = elapsed
            )
        } catch (failure: Exception) {
            try { balanceWorker.shutdownNow() }
            catch (cleanup: Exception) {
                if (cleanup !== failure) failure.addSuppressed(cleanup)
            }
            throw failure
        }

        var sessionWorker: ExecutorService? = null
        try {
            val sessionScheduler = dependencies.schedulerFactory()
            check(sessionScheduler !== balanceScheduler) {
                "Monitoring schedulers must be isolated"
            }
            val keyFactory = dependencies.keyFactory(venue, keys.key)
            val socketFactory = dependencies.socketFactory(venue) {
                    dependencies.schedulerFactory().also {
                        check(it !== balanceScheduler && it !== sessionScheduler) {
                            "Socket scheduler must be isolated"
                        }
                    }
                }
            val worker = dependencies.workerFactory()
            check(worker !== balanceWorker) {
                "Monitoring workers must be isolated"
            }
            sessionWorker = worker
            val session = AccountSessionManager(
                keyFactory = keyFactory,
                socketFactory = socketFactory,
                worker = worker,
                scheduler = sessionScheduler,
                rateLimitGuard = scope.guard,
                onState = { state ->
                    handleSessionState(token, state)
                },
                onEvent = { event ->
                    handleAccountEvent(token, event)
                },
                elapsed = elapsed
            )

            return Resources(
                token = token,
                balance = balance,
                session = session
            )

        } catch (failure: Exception) {
            try { sessionWorker?.shutdownNow() }
            catch (cleanup: Exception) {
                if (cleanup !== failure) failure.addSuppressed(cleanup)
            }
            try {
                balance.close()
            } catch (cleanup: Exception) {
                if (cleanup !== failure) {
                    failure.addSuppressed(cleanup)
                }
            }

            throw failure
        }
    }

    private fun accepted(
        token: AccountSessionIsolation.Token
    ): Resources? {

        requireOwner()

        if (closed || !lifecycle.accepts(token)) {
            return null
        }

        return active?.takeIf {
            it.token === token
        }
    }

    private fun handleSessionState(
        token: AccountSessionIsolation.Token,
        state: AccountSessionSnapshot
    ) {
        val resources = accepted(token) ?: return

        val previous = resources.previousState

        resources.previousState = state.state

        val generation =
            resources.balanceGeneration ?: return

        val wasConnected =
            previous == AccountSessionState.CONNECTED

        val isConnected =
            state.state == AccountSessionState.CONNECTED

        if (wasConnected != isConnected) {
            try {
                resources.balance.invalidate(generation)
            } catch (failure: Exception) {
                resources.callbackFailed = true
                throw failure
            }
        }
    }

    private fun handleAccountEvent(
        token: AccountSessionIsolation.Token,
        event: AccountStreamEvent
    ) {
        val resources = accepted(token) ?: return

        if (
            resources.session.snapshot().state !=
                AccountSessionState.CONNECTED
        ) {
            return
        }

        val generation =
            resources.balanceGeneration ?: return

        try {
            resources.balance.onAccountEvent(generation, event)
        } catch (failure: Exception) {
            resources.callbackFailed = true
            throw failure
        }
    }

    fun start(
        venue: FuturesVenue,
        snapshot: CredentialRevisionSnapshot
    ): Boolean {

        requireOwner()
        check(!closed)

        require(
            venue != FuturesVenue.REAL ||
                BuildConfig.MAINNET_ORDERS
        )

        stop()

        pendingCredentials = snapshot.credentials

        try {
            val token = lifecycle.activate(
                venue,
                snapshot.revision
            )

            if (token == null) {
                active = null
                return false
            }

            val resources = checkNotNull(active)

            check(resources.token === token)

            resources.balance.start()

            resources.balanceGeneration =
                resources.balance.snapshot().generation

            if (!lifecycle.refresh()) {
                active = null
                return false
            }

            resources.session.start()

            if (resources.callbackFailed) {
                stop()
                return false
            }

            if (!lifecycle.refresh()) {
                active = null
                return false
            }

            return true

        } catch (failure: Exception) {

            try {
                stop()
            } catch (cleanup: Exception) {
                if (cleanup !== failure) {
                    failure.addSuppressed(cleanup)
                }
            }

            throw failure

        } finally {
            pendingCredentials = null
        }
    }

    fun refresh(): Boolean {
        requireOwner()

        if (closed) return false

        val resources = active ?: return false

        try {
            if (!lifecycle.refresh()) {
                active = null
                return false
            }

            if (
                resources.callbackFailed ||
                resources.session.snapshot().state ==
                    AccountSessionState.CLOSED ||
                resources.balance.snapshot().status ==
                    AccountBalanceStatus.CLOSED
            ) {
                stop()
                return false
            }

            return true

        } catch (failure: Exception) {
            try {
                stop()
            } catch (cleanup: Exception) {
                if (cleanup !== failure) failure.addSuppressed(cleanup)
            }
            throw failure
        }
    }

    fun snapshot(): AccountMonitoringSnapshot? {

        requireOwner()

        val current = active ?: return null

        val resources =
            accepted(current.token) ?: return null

        if (resources.callbackFailed) return null

        val session = resources.session.snapshot()

        val balance = resources.balance.snapshot()

        val visible = if (
            session.state == AccountSessionState.CONNECTED &&
            balance.generation ==
                resources.balanceGeneration &&
            balance.venue == resources.token.venue
        ) {
            balance.freshBalance(elapsed())
        } else {
            null
        }

        if (!lifecycle.accepts(resources.token)) {
            return null
        }

        return AccountMonitoringSnapshot(
            venue = resources.token.venue,
            session = session,
            balanceStatus = if (visible == null &&
                (balance.status == AccountBalanceStatus.READY ||
                    balance.status == AccountBalanceStatus.REFRESHING)
            ) AccountBalanceStatus.STALE else balance.status,
            balance = visible
        )
    }

    fun stop() {
        requireOwner()

        if (closed) return

        active = null
        pendingCredentials = null

        lifecycle.stop()
    }

    override fun close() {
        requireOwner()

        if (closed) return

        closed = true
        active = null
        pendingCredentials = null

        lifecycle.close()
    }
}
