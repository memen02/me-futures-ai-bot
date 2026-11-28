package com.mefuturesaibot.app.ai

import org.junit.Assert.*
import org.junit.Test

class UnifiedDepthSequenceGuardTest {
    @Test fun gapRequiresNewSnapshot() {
        val guard = UnifiedDepthSequenceGuard()
        guard.reset(100)
        assertFalse(guard.synchronized())
        assertFalse(guard.accept(90,99,89))
        assertTrue(guard.accept(99,101,98))
        assertTrue(guard.synchronized())
        assertTrue(guard.accept(102,105,101))
        assertFalse(guard.accept(107,110,106))
        assertFalse(guard.synchronized())
        assertFalse(guard.accept(111,112,110))
        guard.reset(112)
        assertTrue(guard.accept(111,113,110))
        guard.invalidate()
        assertFalse(guard.synchronized())
    }
    @Test fun missingAnchorAndMalformedUpdateFailClosed() {
        val guard = UnifiedDepthSequenceGuard()
        assertFalse(guard.accept(1,2,0))
        guard.reset(10)
        assertFalse(guard.accept(12,11,10))
        assertFalse(guard.accept(9,11,8))
    }
}
