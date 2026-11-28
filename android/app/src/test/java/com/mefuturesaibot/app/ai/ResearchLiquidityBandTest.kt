package com.mefuturesaibot.app.ai

import org.junit.Assert.*
import org.junit.Test

class ResearchLiquidityBandTest {
    private val bids = listOf(99.99 to 4.0,99.8 to 1.0)
    private val asks = listOf(100.01 to 1.0,100.2 to 1.0)
    @Test fun unverifiedOrIncompleteDepthIsRejected() {
        assertNull(ResearchLiquidityBand.calculate("BTCUSDT",1000,bids,asks,false))
        assertNull(ResearchLiquidityBand.calculate("BTCUSDT",1000,bids,listOf(100.01 to 1.0,100.02 to 1.0),true))
        assertNull(ResearchLiquidityBand.calculate("BTCUSDT",1000,bids.reversed(),asks,true))
    }
    @Test fun symmetricCoveredBandProducesNotional() {
        val book = requireNotNull(ResearchLiquidityBand.calculate("BTCUSDT",1000,bids,asks,true))
        assertEquals(399.96,book.bidNotional,0.00001)
        assertEquals(100.01,book.askNotional,0.00001)
    }
    @Test fun budgetEnforcesCooldown() {
        val guard = AiRateLimitGuard(1000,60)
        assertTrue(guard.acquire(1000,2))
        guard.onHttpResponse(429,60,1000)
        guard.release()
        assertFalse(guard.acquire(60999,2))
        assertTrue(guard.acquire(61000,2))
    }
}
