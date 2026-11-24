package com.mefuturesaibot.app.scanner

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class TradingLedgerTest {
    @Test fun mixedMonthsMigrateAndTotalsSurviveDetailRotation() {
        val jan = Instant.parse("2025-01-31T23:59:59Z").toEpochMilli()
        val feb = jan + 1000
        var ledger = TradeLedger.legacy(listOf("$feb|Tutup SOLUSDT · stop-loss · net -1.000 USDT",
            "$jan|Tutup BTCUSDT · take-profit · net +2.250 USDT"),3)
        assertTrue(ledger.incomplete)
        repeat(170) { ledger = ledger.add(TradeClose(feb+it,"ETHUSDT",0.5,"TP")) }
        assertEquals(150,ledger.recent.size)
        val saved = TradeLedger.read(ledger.json())
        assertEquals(2.25,saved.months.getValue("2025-01").gain,0.0)
        assertEquals(84.0,saved.months.getValue("2025-02").let { it.gain-it.loss },0.0)
    }
    @Test fun manualPaperCloseUsesQuoteAndStaysOutOfStrategyMetrics() {
        val position = PaperPosition("ETHUSDT",1,100.0,1000,1.0,95.0,110.0,manual=true)
        val account = PaperAccount(position=position)
        val scan = Scan(time=3000,ok=true)
        val closed = account.advance(scan,"ETHUSDT",null,true,Quote(102.0,102.1,3000),exitRequested=true)
        assertEquals(1,closed.trades)
        assertEquals(0,closed.measured)
        assertEquals("ditutup pengguna",closed.ledger.recent.single().reason)
        assertEquals(closed,closed.advance(scan,"ETHUSDT",null,true,Quote(102.0,102.1,3000),exitRequested=true))
    }
    @Test fun trailingMirrorsLongAndShortAndPersistsTheInitialRisk() {
        for (side in listOf(1,-1)) {
            val p = PaperPosition("ETHUSDT",side,100.0,1000,1.0,100.0-side*2,100.0+side*9,trailing=true)
            val a = PaperAccount(position=p)
            fun step(a: PaperAccount, t: Long, px: Double) = a.advance(Scan(time=t,ok=true),"ETHUSDT",null,true,Quote(px,px,t))
            val b = step(a,2000,100.0+side*5)
            assertEquals(100.0+side*3,b.position!!.stop,0.000001)
            val c = PaperAccount.read(b.json())
            assertEquals(p.initialStop,c.position!!.initialStop,0.0)
            val d = step(c,3000,100.0+side*4)
            assertEquals(b.position.stop,d.position!!.stop,0.0)
            val closed = step(d,4000,100.0+side*2)
            assertNull(closed.position)
            assertEquals("trailing stop",closed.ledger.recent.single().reason)
            assertTrue(closed.ledger.recent.single().net!! > 0)
        }
    }
}
