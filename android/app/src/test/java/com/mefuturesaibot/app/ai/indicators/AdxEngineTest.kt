package com.mefuturesaibot.app.ai.indicators
import org.junit.Test
import org.junit.Assert.*

class AdxEngineTest {
    @Test fun monotonicRiseHasPositiveDirection() {
        val c = List(40) { 100.0 + it }
        val result = AdxEngine.value(c.map { it+1 }, c.map { it-1 }, c)!!
        assertEquals(100.0, result.adx, 1e-8)
        assertEquals(0.0, result.minusDi, 1e-8)
        assertTrue(result.plusDi > 0)
    }
    @Test fun flatMarketHasNoDirectionalStrength() {
        val c = List(40) { 100.0 }
        val result = AdxEngine.value(c,c,c)!!
        assertEquals(0.0, result.adx, 1e-8)
    }
    @Test fun insufficientHistoryIsUnavailable() {
        val c = List(27) { 100.0 }
        assertNull(AdxEngine.value(c,c,c))
    }
}
