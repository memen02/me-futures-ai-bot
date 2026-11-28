package com.mefuturesaibot.app.ai

import org.junit.Assert.*
import org.junit.Test

class ResearchDepthBookTest {
    private fun level(p:String,q:String)=p.toBigDecimal() to q.toBigDecimal()
    private fun delta(first:Long,last:Long,previous:Long)=DepthDelta(first,last,previous,10000,
        listOf(level("99.99","4")),emptyList())
    @Test fun snapshotReplayAndGap() {
        val book=ResearchDepthBook()
        book.delta(delta(99,101,98))
        assertTrue(book.needsSnapshot())
        assertNull(book.liquidity())
        book.anchor(100,listOf(level("99.99","1"),level("99.8","1")),
            listOf(level("100.01","1"),level("100.2","1")))
        assertEquals(399.96,requireNotNull(book.liquidity()).bidNotional,0.00001)
        book.delta(delta(104,105,103))
        assertNull(book.liquidity())
    }
    @Test fun cachedLiquidityInvalidatesOnDeltaAndClear() {
        val book=ResearchDepthBook()
        book.delta(delta(99,101,98))
        book.anchor(100,listOf(level("99.99","1"),level("99.8","1")),
            listOf(level("100.01","1"),level("100.2","1")))
        val first=requireNotNull(book.liquidity())
        assertSame(first,book.liquidity())
        book.delta(DepthDelta(102,102,101,10001,listOf(level("99.99","5")),emptyList()))
        val second=requireNotNull(book.liquidity())
        assertNotSame(first,second)
        assertEquals(499.95,second.bidNotional,0.00001)
        book.clear()
        assertNull(book.liquidity())
    }
    @Test fun pendingLevelBudgetInvalidatesEpoch() {
        val book=ResearchDepthBook()
        val levels=List(1000) { level("99.99","1") }
        val initial=book.epoch()
        repeat(4) { book.delta(DepthDelta(1,1,0,10000,levels,levels)) }
        assertEquals(initial,book.epoch())
        book.delta(DepthDelta(1,1,0,10000,levels,levels))
        assertTrue(book.epoch()>initial)
        assertNull(book.liquidity())
    }
    @Test fun disconnectClearsBook() {
        val book=ResearchDepthBook()
        book.delta(delta(99,101,98))
        book.clear()
        assertFalse(book.needsSnapshot())
        assertNull(book.liquidity())
    }
}
