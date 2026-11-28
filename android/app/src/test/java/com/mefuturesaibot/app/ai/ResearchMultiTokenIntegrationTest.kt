package com.mefuturesaibot.app.ai

import org.junit.Assert.*
import org.junit.Test

class ResearchMultiTokenIntegrationTest {
    private fun level(p:String,q:String)=p.toBigDecimal() to q.toBigDecimal()
    private fun ready(symbol:String): ResearchDepthBook {
        val book=ResearchDepthBook(symbol)
        book.delta(DepthDelta(99,101,98,10000,emptyList(),emptyList()))
        book.anchor(100,listOf(level("99.99","4"),level("99.8","1")),
            listOf(level("100.01","1"),level("100.2","1")))
        return book
    }
    @Test fun gapInOneTokenDoesNotInvalidateOtherToken() {
        val btc=ready("BTCUSDT")
        val eth=ready("ETHUSDT")
        assertEquals("BTCUSDT",requireNotNull(btc.liquidity()).symbol)
        assertEquals("ETHUSDT",requireNotNull(eth.liquidity()).symbol)
        btc.delta(DepthDelta(105,106,104,10001,emptyList(),emptyList()))
        assertNull(btc.liquidity())
        assertNotNull(eth.liquidity())
    }
    @Test fun bufferOverflowChangesEpoch() {
        val book=ResearchDepthBook()
        val epoch=book.epoch()
        repeat(65) { book.delta(DepthDelta(99,101,98,10000,emptyList(),emptyList())) }
        assertTrue(book.epoch()>epoch)
        assertNull(book.liquidity())
    }
}
