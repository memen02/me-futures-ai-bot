package com.mefuturesaibot.app.scanner

import com.mefuturesaibot.app.exchange.PositionFeed
import org.junit.Assert.*
import org.junit.Test

class PositionMonitorTest {
    @Test fun eventMustBeFreshAndStrictlyNewer() {
        assertTrue(PositionFeed.timely(10001,10000,10003))
        assertFalse(PositionFeed.timely(10000,10000,10001))
        assertFalse(PositionFeed.timely(9999,10000,10001))
        assertFalse(PositionFeed.timely(10001,10000,15002))
        assertFalse(PositionFeed.timely(12002,10000,11000))
    }
    @Test fun restartSettlesTheCapturedStopWithFundingExactlyOnce() {
        val stamp = 20000L
        val quote = Quote(94.0,94.1,stamp)
        val p = PaperPosition("ETHUSDT",1,100.0,1000,1.0,95.0,120.0,exitQuote=quote,exitReason="stop-loss")
        val restored = PaperAccount.read(PaperAccount(position=p).json())
        val held = restored.position!!
        assertEquals(quote,held.exitQuote)
        val payment = Funding(13000,0.001,100.0)
        val closed = restored.advance(Scan(time=stamp,ok=true),held.symbol,null,true,held.exitQuote,
            listOf(payment,payment),latchedReason=held.exitReason)
        assertEquals(1,closed.trades)
        assertEquals("stop-loss",closed.ledger.recent.single().reason)
        val fill=quote.bid*(1-RiskPlan.SLIP)
        val net=fill-100 - (100+fill)*RiskPlan.FEE - 0.1
        assertEquals(net,closed.ledger.recent.single().net!!,1e-10)
        assertEquals(closed,closed.advance(Scan(time=stamp,ok=true),held.symbol,null,true,quote))
    }
}
