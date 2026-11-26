package com.mefuturesaibot.app.ai.indicators

import org.junit.Test
import org.junit.Assert.*

internal class BollingerEngineTest {
    @Test fun flatPricesHaveZeroWidth() {
        val bands = BollingerEngine.bands(List(20) { 100.0 })!!
        assertEquals(100.0, bands.middle, 1e-9)
        assertEquals(bands.middle, bands.upper, 1e-9)
        assertEquals(bands.middle, bands.lower, 1e-9)
    }
    @Test fun knownPopulationDeviation() {
        val bands = BollingerEngine.bands(listOf(1.0, 3.0), 2)!!
        assertEquals(2.0, bands.middle, 1e-9)
        assertEquals(4.0, bands.upper, 1e-9)
        assertEquals(0.0, bands.lower, 1e-9)
    }
    @Test fun rejectsInvalidInput() {
        assertNull(BollingerEngine.bands(listOf(1.0)))
        assertNull(BollingerEngine.bands(List(20) { Double.NaN }))
        assertNull(BollingerEngine.bands(List(20) { 1.0 }, multiplier = -1.0))
    }
}
