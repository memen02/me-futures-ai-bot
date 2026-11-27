package com.mefuturesaibot.app.ai.execution

import com.mefuturesaibot.app.demo.FuturesVenue
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class OrderSafetyTest {
    @Test fun identitySeparatesAccountsAndVenues() {
        val id = OrderIntentIdentity(FuturesVenue.DEMO,"a".repeat(64),"BTCUSDT","v1",1000)
        assertTrue(id.valid())
        assertEquals(id.key(),id.copy().key())
        assertNotEquals(id.key(),id.copy(venue=FuturesVenue.REAL).key())
        assertNotEquals(id.key(),id.copy(accountId="b".repeat(64)).key())
        assertFalse(id.copy(symbol="BTC|USDT").valid())
        assertTrue(id.newClientOrderId().length <= 36)
    }

    @Test fun ambiguousOrderCannotReturnToSubmission() {
        assertTrue(OrderStateTransitionEngine.allowed(OrderState.RESERVED,OrderState.SUBMITTING))
        assertTrue(OrderStateTransitionEngine.allowed(OrderState.SUBMITTING,OrderState.UNKNOWN))
        for (state in OrderState.entries.filter { it != OrderState.RESERVED }) {
            assertFalse(OrderStateTransitionEngine.allowed(state,OrderState.SUBMITTING))
            assertFalse(OrderStateTransitionEngine.allowed(state,OrderState.RESERVED))
        }
    }

    @Test fun missingOrStaleObservationFreezes() {
        val order = ReservedOrder("key","client")
        val fill = OrderObservation("client",OrderState.PARTIALLY_FILLED,BigDecimal.ONE,1000)
        assertEquals(ReconciliationAction.FREEZE,OrderReconciliationEngine.assess(order,null,2000))
        assertEquals(ReconciliationAction.FREEZE,OrderReconciliationEngine.assess(order,fill,6000))
        assertEquals(ReconciliationAction.FREEZE,OrderReconciliationEngine.assess(order,fill.copy(clientOrderId="other"),2000))
        assertEquals(ReconciliationAction.PROTECT_POSITION,OrderReconciliationEngine.assess(order,fill,2000))
        assertEquals(ReconciliationAction.PROTECT_POSITION,OrderReconciliationEngine.assess(order,fill.copy(state=OrderState.CANCELED),2000))
        assertEquals(ReconciliationAction.REVIEW_TERMINAL,OrderReconciliationEngine.assess(order,fill.copy(state=OrderState.CANCELED,executedQuantity=BigDecimal.ZERO),2000))
    }
}
