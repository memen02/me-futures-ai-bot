package com.mefuturesaibot.app.account

import com.mefuturesaibot.app.demo.FuturesVenue
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class AccountBalanceStoreTest {

    private val store = AccountBalanceStore()

    private fun balance(
        venue: FuturesVenue = FuturesVenue.DEMO,
        received: Long = 1000L
    ): AccountBalance {
        return AccountBalance(
            venue = venue,
            wallet = BigDecimal("100.00"),
            equity = BigDecimal("105.00"),
            available = BigDecimal("80.00"),
            unrealizedPnl = BigDecimal("5.00"),
            receivedElapsed = received
        )
    }

    private fun start(
        venue: FuturesVenue = FuturesVenue.DEMO
    ): Long {
        return checkNotNull(store.start(venue))
    }

    private fun request(
        generation: Long
    ): AccountBalanceRequest {
        return checkNotNull(
            store.beginRead(generation)
        )
    }

    private fun ready(
        venue: FuturesVenue = FuturesVenue.DEMO
    ): Long {
        val generation = start(venue)

        val read = request(generation)

        assertTrue(
            store.accept(
                read,
                balance(venue),
                1000L
            )
        )

        return generation
    }

    @Test
    fun initialStateIsStopped() {
        val snapshot = store.snapshot(0L)

        assertEquals(
            AccountBalanceStatus.STOPPED,
            snapshot.status
        )

        assertEquals(0L, snapshot.generation)
        assertNull(snapshot.venue)
        assertNull(snapshot.balance)
        assertNull(snapshot.freshBalance(0L))
        assertTrue(snapshot.invalidated)
        assertFalse(snapshot.requestPending)

        assertFalse(store.isActive(0L))
        assertNull(store.beginRead(0L))
        assertFalse(store.invalidate(0L))
        assertFalse(store.stop())
    }

    @Test
    fun startDemoCreatesLoadingSession() {
        val generation = start()

        val snapshot = store.snapshot(0L)

        assertEquals(1L, generation)

        assertEquals(
            AccountBalanceStatus.LOADING,
            snapshot.status
        )

        assertEquals(
            FuturesVenue.DEMO,
            snapshot.venue
        )

        assertEquals(
            generation,
            snapshot.generation
        )

        assertTrue(store.isActive(generation))
        assertNull(snapshot.balance)
        assertNull(snapshot.freshBalance(0L))
        assertFalse(snapshot.requestPending)
    }

    @Test
    fun duplicateStartDoesNotReplaceSession() {
        val generation = start()

        val read = request(generation)

        assertNull(
            store.start(FuturesVenue.DEMO)
        )

        assertNull(
            store.start(FuturesVenue.REAL)
        )

        assertTrue(store.isCurrent(read))

        val snapshot = store.snapshot(0L)

        assertEquals(
            generation,
            snapshot.generation
        )

        assertEquals(
            FuturesVenue.DEMO,
            snapshot.venue
        )
    }

    @Test
    fun validBalanceBecomesReady() {
        val generation = start()

        val read = request(generation)

        val result = balance()

        assertTrue(
            store.accept(read, result, 1000L)
        )

        val snapshot = store.snapshot(1000L)

        assertEquals(
            AccountBalanceStatus.READY,
            snapshot.status
        )

        assertEquals(result, snapshot.balance)

        assertEquals(
            result,
            snapshot.freshBalance(1000L)
        )

        assertFalse(snapshot.invalidated)
        assertFalse(snapshot.requestPending)
        assertFalse(store.isCurrent(read))
    }

    @Test
    fun freshnessExpiresAfterExactBoundary() {
        ready()

        val boundary =
            1000L + AccountBalance.MAX_AGE_MS

        val fresh = store.snapshot(boundary)

        assertEquals(
            AccountBalanceStatus.READY,
            fresh.status
        )

        assertNotNull(
            fresh.freshBalance(boundary)
        )

        val expired =
            store.snapshot(boundary + 1L)

        assertEquals(
            AccountBalanceStatus.STALE,
            expired.status
        )

        assertNull(
            expired.freshBalance(boundary + 1L)
        )

        assertNull(
            fresh.freshBalance(boundary + 1L)
        )

        assertNotNull(expired.balance)
    }

    @Test
    fun futureAndNegativeTimestampsAreRejected() {
        val generation = start()

        val future = request(generation)

        assertFalse(
            store.accept(
                future,
                balance(received = 1001L),
                1000L
            )
        )

        assertEquals(
            AccountBalanceStatus.RETRY_WAIT,
            store.snapshot(1000L).status
        )

        val negative = request(generation)

        assertFalse(
            store.accept(
                negative,
                balance(received = -1L),
                1000L
            )
        )

        val snapshot = store.snapshot(1000L)

        assertNull(snapshot.balance)
        assertFalse(snapshot.requestPending)
    }

    @Test
    fun expiredResultIsRejected() {
        val generation = start()

        val read = request(generation)

        val now =
            1000L + AccountBalance.MAX_AGE_MS + 1L

        assertFalse(
            store.accept(read, balance(), now)
        )

        val snapshot = store.snapshot(now)

        assertEquals(
            AccountBalanceStatus.RETRY_WAIT,
            snapshot.status
        )

        assertNull(snapshot.balance)
        assertNull(snapshot.freshBalance(now))
        assertFalse(snapshot.requestPending)
    }

    @Test
    fun wrongVenueResultIsRejected() {
        val generation = start()

        val read = request(generation)

        assertFalse(
            store.accept(
                read,
                balance(FuturesVenue.REAL),
                1000L
            )
        )

        val snapshot = store.snapshot(1000L)

        assertEquals(
            FuturesVenue.DEMO,
            snapshot.venue
        )

        assertEquals(
            AccountBalanceStatus.RETRY_WAIT,
            snapshot.status
        )

        assertNull(snapshot.balance)
        assertFalse(snapshot.requestPending)
    }

    @Test
    fun onlyOneReadCanBePending() {
        val generation = ready()

        val read = request(generation)

        assertNull(
            store.beginRead(generation)
        )

        assertNull(
            store.beginRead(generation + 1L)
        )

        assertTrue(store.isCurrent(read))

        val snapshot = store.snapshot(1000L)

        assertEquals(
            AccountBalanceStatus.REFRESHING,
            snapshot.status
        )

        assertTrue(snapshot.requestPending)

        assertNotNull(
            snapshot.freshBalance(1000L)
        )
    }

    @Test
    fun oldRequestCannotCompleteNewRead() {
        val generation = start()

        val old = request(generation)

        assertTrue(store.fail(old))

        val current = request(generation)

        assertTrue(
            current.requestId > old.requestId
        )

        assertFalse(
            store.accept(
                old,
                balance(),
                1000L
            )
        )

        assertFalse(store.fail(old))

        assertTrue(store.isCurrent(current))

        assertTrue(
            store.accept(
                current,
                balance(),
                1000L
            )
        )

        assertFalse(
            store.accept(
                current,
                balance(),
                1000L
            )
        )
    }

    @Test
    fun accountEventInvalidatesCachedBalance() {
        val generation = ready()

        assertTrue(
            store.invalidate(generation)
        )

        val stale = store.snapshot(1000L)

        assertEquals(
            AccountBalanceStatus.STALE,
            stale.status
        )

        assertTrue(stale.invalidated)
        assertNotNull(stale.balance)
        assertNull(stale.freshBalance(1000L))

        val read = request(generation)

        assertTrue(
            store.accept(
                read,
                balance(received = 2000L),
                2000L
            )
        )

        assertEquals(
            AccountBalanceStatus.READY,
            store.snapshot(2000L).status
        )
    }

    @Test
    fun eventDuringReadRequiresAnotherSync() {
        val generation = start()

        val first = request(generation)

        assertTrue(
            store.invalidate(generation)
        )

        assertTrue(
            store.invalidate(generation)
        )

        assertTrue(
            store.accept(
                first,
                balance(),
                1000L
            )
        )

        val stale = store.snapshot(1000L)

        assertEquals(
            AccountBalanceStatus.STALE,
            stale.status
        )

        assertTrue(stale.invalidated)
        assertFalse(stale.requestPending)
        assertNull(stale.freshBalance(1000L))

        val second = request(generation)

        assertTrue(
            store.accept(
                second,
                balance(received = 2000L),
                2000L
            )
        )

        val ready = store.snapshot(2000L)

        assertEquals(
            AccountBalanceStatus.READY,
            ready.status
        )

        assertFalse(ready.invalidated)
        assertNotNull(ready.freshBalance(2000L))
    }

    @Test
    fun olderTimestampCannotReplaceNewerBalance() {
        val generation = ready()

        val read = request(generation)

        assertFalse(
            store.accept(
                read,
                balance(received = 999L),
                1000L
            )
        )

        val snapshot = store.snapshot(1000L)

        assertEquals(
            AccountBalanceStatus.RETRY_WAIT,
            snapshot.status
        )

        assertEquals(
            balance(),
            snapshot.balance
        )

        assertNull(snapshot.freshBalance(1000L))
        assertFalse(snapshot.requestPending)
    }

    @Test
    fun failedRefreshRecoversWithNewRead() {
        val generation = ready()

        val failed = request(generation)

        assertTrue(store.fail(failed))

        val retry = store.snapshot(1000L)

        assertEquals(
            AccountBalanceStatus.RETRY_WAIT,
            retry.status
        )

        assertNotNull(retry.balance)
        assertNull(retry.freshBalance(1000L))

        assertTrue(
            store.invalidate(generation)
        )

        assertEquals(
            AccountBalanceStatus.RETRY_WAIT,
            store.snapshot(1000L).status
        )

        val next = request(generation)

        assertNull(
            store.snapshot(1000L)
                .freshBalance(1000L)
        )

        assertTrue(
            store.accept(
                next,
                balance(received = 2000L),
                2000L
            )
        )

        assertEquals(
            AccountBalanceStatus.READY,
            store.snapshot(2000L).status
        )
    }

    @Test
    fun stopClearsBalanceAndPendingRequest() {
        val generation = ready()

        val read = request(generation)

        assertTrue(store.stop())

        val stopped = store.snapshot(1000L)

        assertEquals(
            AccountBalanceStatus.STOPPED,
            stopped.status
        )

        assertNull(stopped.venue)
        assertNull(stopped.balance)
        assertNull(stopped.freshBalance(1000L))
        assertFalse(stopped.requestPending)
        assertTrue(stopped.invalidated)

        assertFalse(store.isActive(generation))
        assertFalse(store.isCurrent(read))

        assertFalse(
            store.accept(
                read,
                balance(),
                1000L
            )
        )

        assertFalse(store.fail(read))
        assertFalse(store.stop())
    }

    @Test
    fun restartedSessionRejectsOldGeneration() {
        val oldGeneration = start()

        val old = request(oldGeneration)

        assertTrue(store.stop())

        val generation = start()

        val current = request(generation)

        assertTrue(generation > oldGeneration)

        assertEquals(
            old.requestId,
            current.requestId
        )

        assertFalse(
            store.invalidate(oldGeneration)
        )

        assertNull(
            store.beginRead(oldGeneration)
        )

        assertFalse(
            store.accept(
                old,
                balance(),
                1000L
            )
        )

        assertFalse(store.fail(old))

        assertTrue(store.isCurrent(current))

        assertTrue(
            store.accept(
                current,
                balance(),
                1000L
            )
        )
    }

    @Test
    fun demoAndRealSessionsRemainIsolated() {
        val demo = ready(FuturesVenue.DEMO)

        val old = request(demo)

        assertTrue(store.stop())

        val real = start(FuturesVenue.REAL)

        val snapshot = store.snapshot(1000L)

        assertEquals(
            FuturesVenue.REAL,
            snapshot.venue
        )

        assertNull(snapshot.balance)

        val current = request(real)

        assertFalse(
            store.accept(
                old,
                balance(FuturesVenue.DEMO),
                1000L
            )
        )

        assertTrue(store.isCurrent(current))

        assertTrue(
            store.accept(
                current,
                balance(FuturesVenue.REAL),
                1000L
            )
        )

        assertEquals(
            FuturesVenue.REAL,
            store.snapshot(1000L)
                .freshBalance(1000L)?.venue
        )

        assertTrue(store.stop())

        start(FuturesVenue.DEMO)

        assertNull(
            store.snapshot(1000L).balance
        )
    }

    @Test
    fun closePermanentlyClearsAndDisablesStore() {
        val generation = ready()

        val read = request(generation)

        assertTrue(store.close())

        val closed = store.snapshot(1000L)

        assertEquals(
            AccountBalanceStatus.CLOSED,
            closed.status
        )

        assertNull(closed.venue)
        assertNull(closed.balance)
        assertNull(closed.freshBalance(1000L))
        assertFalse(closed.requestPending)

        assertFalse(store.isActive(generation))
        assertFalse(store.isCurrent(read))
        assertFalse(store.invalidate(generation))

        assertNull(store.beginRead(generation))
        assertNull(store.start(FuturesVenue.DEMO))
        assertNull(store.start(FuturesVenue.REAL))

        assertFalse(
            store.accept(
                read,
                balance(),
                1000L
            )
        )

        assertFalse(store.fail(read))
        assertFalse(store.stop())
        assertFalse(store.close())
    }
}
