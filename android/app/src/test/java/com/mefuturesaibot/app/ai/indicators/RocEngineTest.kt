package com.mefuturesaibot.app.ai.indicators

import org.junit.Test
import org.junit.Assert.*

internal class RocEngineTest {
    @Test fun risingAndFallingMomentum() {
        assertEquals(25.0, RocEngine.value(listOf(100.0, 125.0), 1)!!, 1e-9)
        assertEquals(-20.0, RocEngine.value(listOf(125.0, 100.0), 1)!!, 1e-9)
    }
    @Test fun rejectsMissingOrInvalidHistory() {
        assertNull(RocEngine.value(listOf(100.0)))
        assertNull(RocEngine.value(listOf(0.0, 100.0), 1))
        assertNull(RocEngine.value(listOf(1.0, Double.POSITIVE_INFINITY), 1))
    }
}
