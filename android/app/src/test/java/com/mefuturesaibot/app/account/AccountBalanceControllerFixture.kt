package com.mefuturesaibot.app.account

import com.mefuturesaibot.app.demo.FuturesVenue
import com.mefuturesaibot.app.exchange.Scheduler
import java.math.BigDecimal
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.*

/**
 * Thread-safe virtual scheduler.
 *
 * Queue mutations may occur on worker threads.
 * Actions execute only on the constructing thread,
 * outside the scheduler lock.
 */
internal class BalanceTestScheduler : Scheduler {

    private data class Scheduled(
        val due: Long,
        val action: () -> Unit
    )

    private val owner = Thread.currentThread()

    private val lock = Any()

    private val clock = AtomicLong(0L)

    private val ready = ArrayDeque<() -> Unit>()

    private val delayed = mutableListOf<Scheduled>()

    val posts = AtomicInteger(0)

    val delayedPosts = AtomicInteger(0)

    val cancellations = AtomicInteger(0)

    val now: Long
        get() = clock.get()

    val readyCount: Int
        get() = synchronized(lock) {
            ready.size
        }

    val delayedCount: Int
        get() = synchronized(lock) {
            delayed.size
        }

    val nextDue: Long?
        get() = synchronized(lock) {
            delayed.minOfOrNull { it.due }
        }

    private fun requireOwner() {
        check(Thread.currentThread() === owner)
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
            delayed.add(
                Scheduled(
                    AccountBalancePolicy.deadline(
                        now,
                        delayMs
                    ),
                    action
                )
            )

            delayedPosts.incrementAndGet()
        }
    }

    override fun cancelAll() {
        synchronized(lock) {
            ready.clear()
            delayed.clear()
            cancellations.incrementAndGet()
        }
    }

    fun drainReady() {
        requireOwner()

        var count = 0

        while (true) {
            val action = synchronized(lock) {
                ready.pollFirst()
            } ?: return

            check(++count <= 1000)

            action()
        }
    }

    fun moveClockTo(target: Long) {
        requireOwner()

        require(target >= now)

        clock.set(target)
    }

    fun runDue() {
        requireOwner()

        var count = 0

        drainReady()

        while (true) {
            val next = synchronized(lock) {
                val candidate =
                    delayed.minByOrNull { it.due }

                if (
                    candidate != null &&
                    candidate.due <= now
                ) {
                    delayed.remove(candidate)
                    candidate
                } else {
                    null
                }
            } ?: return

            check(++count <= 1000)

            next.action()

            drainReady()
        }
    }

    fun advanceBy(delta: Long) {
        requireOwner()

        require(delta >= 0L)

        moveClockTo(
            Math.addExact(now, delta)
        )

        runDue()
    }

    /**
     * Capture a queued timer to simulate a callback
     * that escaped scheduler cancellation.
     */
    fun captureNextDelayed(): () -> Unit {
        requireOwner()

        return synchronized(lock) {
            checkNotNull(
                delayed.minByOrNull { it.due }
            ).action
        }
    }

    fun captureNextReady(): () -> Unit {
        requireOwner()

        return synchronized(lock) {
            checkNotNull(ready.peekFirst())
        }
    }
}

/**
 * Actual serial JVM worker with injectable rejection.
 *
 * Never executes REST work inline.
 */
internal class BalanceTestWorker :
    ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        LinkedBlockingQueue<Runnable>(),
        ThreadPoolExecutor.AbortPolicy()
    ) {

    private val rejectNext = AtomicBoolean(false)

    fun rejectNextTask() {
        rejectNext.set(true)
    }

    override fun execute(command: Runnable) {
        if (rejectNext.compareAndSet(true, false)) {
            throw RejectedExecutionException(
                "Injected test rejection"
            )
        }

        super.execute(command)
    }

    fun awaitIdle() {
        submit {}.get(
            5L,
            TimeUnit.SECONDS
        )
    }
}

/**
 * Simulates a network operation that can return
 * after cancellation or interruption.
 */
internal class BalanceTestGate {

    private val entered = CountDownLatch(1)

    private val released = CountDownLatch(1)

    fun awaitEntered() {
        assertTrue(
            "REST worker did not enter",
            entered.await(
                5L,
                TimeUnit.SECONDS
            )
        )
    }

    fun block() {
        entered.countDown()

        val deadline =
            System.nanoTime() +
                TimeUnit.SECONDS.toNanos(10L)

        while (true) {
            val remaining =
                deadline - System.nanoTime()

            check(remaining > 0L) {
                "REST test gate timed out"
            }

            try {
                check(
                    released.await(
                        remaining,
                        TimeUnit.NANOSECONDS
                    )
                )

                return

            } catch (_: InterruptedException) {
                // Intentionally allow late results.
            }
        }
    }

    fun release() {
        released.countDown()
    }
}

/**
 * Display-only fake balance reader.
 */
internal class FakeBalanceAccess(
    val identity: Int,
    private val owner: Thread,
    private val readAction: () -> AccountBalance
) : AccountBalanceAccess {

    val reads = AtomicInteger(0)

    val closes = AtomicInteger(0)

    val closed = AtomicBoolean(false)

    override fun read(): AccountBalance {
        check(Thread.currentThread() !== owner)

        check(!closed.get())

        reads.incrementAndGet()

        return readAction()
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            closes.incrementAndGet()
        }
    }
}

/**
 * Shared fixture for deterministic controller tests
 * and actual worker-thread concurrency tests.
 *
 * No Android clock, network, real credentials,
 * exchange connection or order execution.
 */
internal class AccountBalanceControllerFixture(
    val venue: FuturesVenue = FuturesVenue.DEMO,
    val scheduler: BalanceTestScheduler =
        BalanceTestScheduler(),
    val guard: AccountRateLimitGuard =
        AccountRateLimitGuard(
            elapsed = { scheduler.now },
            wallClock = { 0L }
        )
) : AutoCloseable {

    private val owner = Thread.currentThread()

    val worker = BalanceTestWorker()

    val readers = mutableListOf<FakeBalanceAccess>()

    val states = mutableListOf<AccountBalanceSnapshot>()

    val readCalls = AtomicInteger(0)

    val factoryCalls = AtomicInteger(0)

    val gate = BalanceTestGate()

    private val failFactory = AtomicBoolean(false)

    @Volatile
    var readAction: () -> AccountBalance = {
        balance()
    }

    var stateAction: (AccountBalanceSnapshot) -> Unit = {}

    val controller = AccountBalanceController(
        venue = venue,
        factory = AccountBalanceFactory {
            check(Thread.currentThread() === owner)

            val identity =
                factoryCalls.incrementAndGet()

            if (
                failFactory.compareAndSet(
                    true,
                    false
                )
            ) {
                throw IllegalStateException(
                    "Injected factory failure"
                )
            }

            FakeBalanceAccess(
                identity = identity,
                owner = owner,
                readAction = {
                    readCalls.incrementAndGet()

                    readAction()
                }
            ).also {
                readers.add(it)
            }
        },
        scheduler = scheduler,
        rateLimitGuard = guard,
        onState = { state ->
            states.add(state)

            stateAction(state)
        },
        worker = worker,
        elapsed = { scheduler.now },
        jitterPercent = { 0 }
    )

    val generation: Long
        get() = controller.snapshot().generation

    fun balance(
        target: FuturesVenue = venue,
        received: Long = scheduler.now,
        wallet: String = "100.00",
        equity: String = "105.00",
        available: String = "80.00",
        unrealizedPnl: String = "5.00"
    ): AccountBalance {
        return AccountBalance(
            venue = target,
            wallet = BigDecimal(wallet),
            equity = BigDecimal(equity),
            available = BigDecimal(available),
            unrealizedPnl = BigDecimal(unrealizedPnl),
            receivedElapsed = received
        )
    }

    fun rejectNextFactory() {
        failFactory.set(true)
    }

    fun start() {
        controller.start()

        scheduler.runDue()
    }

    fun completeRest() {
        worker.awaitIdle()

        scheduler.drainReady()
    }

    fun startReady() {
        start()

        completeRest()

        assertStatus(AccountBalanceStatus.READY)
    }

    fun invalidate(
        expectedGeneration: Long = generation
    ) {
        controller.invalidate(expectedGeneration)
    }

    fun event(
        type: AccountStreamEventType,
        expectedGeneration: Long = generation
    ) {
        controller.onAccountEvent(
            expectedGeneration,
            AccountStreamEvent(
                type = type,
                eventTime = 1L
            )
        )
    }

    fun assertStatus(
        expected: AccountBalanceStatus
    ) {
        assertEquals(
            expected,
            controller.snapshot().status
        )
    }

    fun assertNoVisibleBalance() {
        assertNull(
            controller.snapshot().freshBalance(
                scheduler.now
            )
        )
    }

    fun awaitTermination() {
        assertTrue(
            "Balance worker did not terminate",
            worker.awaitTermination(
                5L,
                TimeUnit.SECONDS
            )
        )
    }

    override fun close() {
        gate.release()

        try {
            controller.close()
        } finally {
            worker.shutdownNow()

            awaitTermination()
        }
    }
}
