package com.mefuturesaibot.app.ai.execution

import android.content.Context
import org.robolectric.RuntimeEnvironment
import com.mefuturesaibot.app.demo.FuturesVenue
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class ExecutionDatabaseSafetyTest {
    private val context = RuntimeEnvironment.getApplication()
    private val identity = OrderIntentIdentity(FuturesVenue.DEMO,"a".repeat(64),"BTCUSDT","v1",1000)

    @Test fun concurrentReservationAndRestart() {
        context.deleteDatabase("order_journal_v1.db")
        val journal = OrderJournal(context)
        val pool = Executors.newFixedThreadPool(2)
        val gate = CountDownLatch(1)
        try {
            journal.writableDatabase
            val futures = (1..2).map {
                pool.submit<ReservedOrder?> {
                    check(gate.await(10,TimeUnit.SECONDS))
                    AtomicOrderReservation(journal).reserve(identity,10,20,2000)
                }
            }
            gate.countDown()
            val winner = futures.mapNotNull { it.get(15,TimeUnit.SECONDS) }.single()
            assertTrue(OrderStateTransitionEngine.transition(journal,winner,OrderState.RESERVED,OrderState.SUBMITTING))
            assertFalse(OrderStateTransitionEngine.transition(journal,winner,OrderState.RESERVED,OrderState.SUBMITTING))
            journal.close()
            OrderJournal(context).use { reopened ->
                val pending = OrderRecoveryEngine(reopened).pending(identity.venue,identity.accountId).single()
                assertEquals(winner,pending.order)
                assertEquals(OrderState.SUBMITTING,pending.state)
                assertNull(AtomicOrderReservation(reopened).reserve(identity,10,20,3000))
                assertNull(AtomicOrderReservation(reopened).reserve(identity.copy(symbol="ETHUSDT"),10,20,3000))
                assertTrue(OrderRecoveryEngine(reopened).localJournalClear(FuturesVenue.REAL,identity.accountId))
            }
        } finally { gate.countDown(); pool.shutdownNow(); journal.close(); context.deleteDatabase("order_journal_v1.db") }
    }

    @Test fun aggregateRiskAndPersistentHalt() {
        context.deleteDatabase("order_journal_v1.db")
        try {
            OrderJournal(context).use { journal ->
                val reserve = AtomicOrderReservation(journal)
                val order = reserve.reserve(identity,15,20,2000)!!
                assertTrue(OrderStateTransitionEngine.transition(journal,order,OrderState.RESERVED,OrderState.SUBMITTING))
                assertTrue(OrderStateTransitionEngine.transition(journal,order,OrderState.SUBMITTING,OrderState.ACKNOWLEDGED))
                assertNull(reserve.reserve(identity.copy(symbol="ETHUSDT"),6,20,2000))
                assertNotNull(reserve.reserve(identity.copy(symbol="ETHUSDT"),5,20,2000))
            }
            val latch = EmergencyKillSwitch(context)
            latch.halt(identity.venue,identity.accountId)
            assertFalse(latch.armDemo(identity.accountId,true,false))
            assertTrue(latch.armDemo(identity.accountId,true,true))
            assertTrue(EmergencyKillSwitch(context).entryEnabled(identity.venue,identity.accountId))
            latch.halt(identity.venue,identity.accountId)
            assertFalse(EmergencyKillSwitch(context).entryEnabled(identity.venue,identity.accountId))
            assertFalse(latch.entryEnabled(FuturesVenue.REAL,identity.accountId))
        } finally { context.deleteDatabase("order_journal_v1.db") }
    }
}
