package com.mefuturesaibot.app.ai.indicators

import org.junit.Test
import org.junit.Assert.*

internal class WeightedMovingAverageEngineTest {
    @Test fun weightsRecentPricesMoreHeavily() {
        assertEquals(14.0 / 6.0, WeightedMovingAverageEngine.value(listOf(1.0, 2.0, 3.0), 3)!!, 1e-9)
        assertEquals(10.0 / 6.0, WeightedMovingAverageEngine.value(listOf(3.0, 2.0, 1.0), 3)!!, 1e-9)
    }
    @Test fun rejectsInvalidHistory() {
        assertNull(WeightedMovingAverageEngine.value(listOf(1.0), 2))
        assertNull(WeightedMovingAverageEngine.value(listOf(Double.NaN), 1))
        assertNull(WeightedMovingAverageEngine.value(listOf(1.0), 0))
    }
}
