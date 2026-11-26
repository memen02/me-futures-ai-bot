package com.mefuturesaibot.app.ai.decision

import org.junit.Test
import org.junit.Assert.*

class ExternalSnapshotTest {
    @Test fun liquidityRejectsWrongSymbolFutureStaleAndUnsynchronizedData() {
        val book = LiquiditySnapshot("BTCUSDT",10000L,80.0,20.0,true)
        assertEquals(EvidenceDirection.LONG,book.direction("BTCUSDT",11999L))
        assertEquals(EvidenceDirection.MISSING,book.direction("BTCUSDT",12000L))
        assertEquals(EvidenceDirection.MISSING,book.direction("BTCUSDT",9999L))
        assertEquals(EvidenceDirection.MISSING,book.direction("ETHUSDT",11000L))
        assertEquals(EvidenceDirection.MISSING,book.copy(synchronized=false).direction("BTCUSDT",11000L))
        assertEquals(EvidenceDirection.MISSING,book.copy(bidNotional=Double.POSITIVE_INFINITY).direction("BTCUSDT",11000L))
    }
}
