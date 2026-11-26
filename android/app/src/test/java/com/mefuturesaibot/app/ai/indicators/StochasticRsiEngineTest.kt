package com.mefuturesaibot.app.ai.indicators
import org.junit.Test
import org.junit.Assert.*

class StochasticRsiEngineTest {
    @Test fun flatRsiRangeIsUnavailable() {
        assertNull(StochasticRsiEngine.value(List(40) { 100.0 }))
    }
    @Test fun insufficientHistoryIsUnavailable() {
        assertNull(StochasticRsiEngine.value(List(20) { 100.0 + it }))
    }
    @Test fun varyingMomentumProducesBoundedValue() {
        val c = List(50) { 100.0 + it + 5.0 * kotlin.math.sin(it.toDouble()) }
        val result = StochasticRsiEngine.value(c)!!
        assertTrue(result in 0.0..100.0)
    }
}
