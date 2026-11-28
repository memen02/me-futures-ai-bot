package com.mefuturesaibot.app.ai

import org.junit.Assert.*
import org.junit.Test

class ResearchDepthCoverageTest {
    private fun l(p:String,q:String)=p.toBigDecimal() to q.toBigDecimal()
    @Test fun isolatedSymbolAndEpoch() {
        val book=ResearchDepthBook("ETHUSDT")
        val before=book.epoch()
        book.delta(DepthDelta(99,101,98,10000,emptyList(),emptyList()))
        book.anchor(100,listOf(l("99.99","4"),l("99.8","1")),listOf(l("100.01","1"),l("100.2","1")))
        assertEquals("ETHUSDT",requireNotNull(book.liquidity()).symbol)
        assertTrue(book.epoch()>before)
        book.clear()
        assertNull(book.liquidity())
    }
    @Test fun updatesOutsideSnapshotCannotExtendCoverage() {
        val book=ResearchDepthBook()
        book.delta(DepthDelta(99,101,98,10000,listOf(l("99.8","1")),listOf(l("100.2","1"))))
        book.anchor(100,listOf(l("99.99","4"),l("99.98","1")),listOf(l("100.01","1"),l("100.02","1")))
        assertNull(book.liquidity())
    }
}
