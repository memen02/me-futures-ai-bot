package com.mefuturesaibot.app.ai.execution

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class ExecutionRecoverySafetyTest {
    private val order = ReservedOrder("key","client")
    private fun snapshot() = PositionProtectionSnapshot("client",BigDecimal.ONE,
        BigDecimal("110"),BigDecimal("95"),BigDecimal("120"),1000,true,true)

    @Test fun trailingOnlyTightensVerifiedProtection() {
        val s = snapshot()
        assertEquals(0,SmartTrailingStopExecution.candidate(order,s,100.0,2.0,5.0,
            BigDecimal("0.1"),2000)!!.compareTo(BigDecimal("106")))
        assertNull(SmartTrailingStopExecution.candidate(order,s.copy(stopVerified=false),
            100.0,2.0,5.0,BigDecimal("0.1"),2000))
        assertNull(SmartTrailingStopExecution.candidate(order,s.copy(stop=BigDecimal("107")),
            100.0,2.0,5.0,BigDecimal("0.1"),2000))
        val short = s.copy(signedQuantity=BigDecimal.ONE.negate(),mark=BigDecimal("90"),
            stop=BigDecimal("105"),target=BigDecimal("80"))
        assertEquals(0,SmartTrailingStopExecution.candidate(order,short,100.0,2.0,5.0,
            BigDecimal("0.1"),2000)!!.compareTo(BigDecimal("94")))
    }

    @Test fun protectionRequiresFreshMatchingPosition() {
        val s = snapshot()
        assertEquals(ProtectionAction.VERIFY_PROTECTION,SmartPositionProtection.assess(order,s,2000))
        assertEquals(ProtectionAction.FREEZE,SmartPositionProtection.assess(order,s,6000))
        assertEquals(ProtectionAction.FREEZE,SmartPositionProtection.assess(order,s.copy(clientOrderId="other"),2000))
        assertEquals(ProtectionAction.REVIEW_MISSING_PROTECTION,SmartPositionProtection.assess(order,s.copy(target=null),2000))
    }

    @Test fun recoveryNeverAuthorizesBlindRetry() {
        for (state in OrderState.entries) {
            val observation = OrderObservation("client",state,BigDecimal.ZERO,1000)
            if (state in setOf(OrderState.RESERVED,OrderState.SUBMITTING,OrderState.UNKNOWN))
                assertEquals(ReconciliationAction.FREEZE,OrderReconciliationEngine.assess(order,observation,2000))
            if (state != OrderState.RESERVED)
                assertFalse(OrderStateTransitionEngine.allowed(state,OrderState.SUBMITTING))
        }
    }
}
