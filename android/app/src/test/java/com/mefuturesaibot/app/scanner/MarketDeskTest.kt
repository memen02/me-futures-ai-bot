package com.mefuturesaibot.app.scanner

import org.junit.Assert.*
import org.junit.Test

class MarketDeskTest {
    private val contract = Contract("ETHUSDT", "USDT")
    private fun row(price: Double = 100.0, time: Long = 60_000L, score: Double = 0.65) =
        MarketRow(contract, Ticker(price, 5.0, 150.0, 50.0, 2_000_000.0, 95.0, time), score)
    private fun scan(row: MarketRow) = Scan(listOf(row), row.ticker!!.time, 0, "Aktif", true)

    @Test fun activePerpetualRegistryAndTickerParsing() {
        val registry = """{"symbols":[{"symbol":"ETHUSDT","quoteAsset":"USDT","status":"TRADING","contractType":"PERPETUAL"},
            {"symbol":"OLDUSDT","status":"SETTLING","contractType":"PERPETUAL"},
            {"symbol":"BTC_NEXT","status":"TRADING","contractType":"NEXT_QUARTER"}]}"""
        assertEquals(listOf(contract), MarketData.contracts(registry))
        val payload = """[{"symbol":"ETHUSDT","lastPrice":"100","priceChangePercent":"5","highPrice":"110",
            "lowPrice":"90","quoteVolume":"300000","weightedAvgPrice":"97","closeTime":60000},{"symbol":"INVALID"}]"""
        val ticks = MarketData.tickers(payload)
        assertEquals(setOf("ETHUSDT"), ticks.keys)
        assertEquals(5.0, ticks.getValue("ETHUSDT").change, 0.0)
        assertFalse(ticks.getValue("ETHUSDT").fresh(300_000))
    }

    @Test fun modelTrainsOnlyOnSubsequentFreshSnapshots() {
        val initial = MarketModel()
        val a = row()
        assertNull(initial.score(a.ticker!!))
        val next = row(102.0, 120_000)
        val tickers = mapOf(contract.symbol to next.ticker!!)
        assertSame(initial, initial.learn(scan(a), tickers, 400_000))
        val trained = initial.learn(scan(a), tickers, 120_000)
        assertEquals(1, trained.rounds)
        assertNotEquals(initial.weights, trained.weights)
        val warmed = trained.copy(rounds = 30)
        assertTrue(warmed.score(next.ticker)!! > 0.5)
        assertEquals(warmed, MarketModel.read(warmed.json()))
    }

    @Test fun confirmationUsesNextFreshPriceAndCompatibleDirection() {
        val confirmed = EntryConfirmation(contract.symbol, true, 60_000L)
        assertNull(confirmed.eligible(scan(row())))
        assertNull(confirmed.eligible(scan(row(time = 120_000L, score = 0.30))))
        assertNull(confirmed.eligible(scan(row(time = 210_000L))))
        val next = row(time = 120_000L)
        assertEquals(next, confirmed.eligible(scan(next)))
    }

    @Test fun virtualLedgerIsIdempotentAndBooksBothFees() {
        val entry = row()
        val book = PaperAccount()
        assertSame(book, book.advance(scan(entry), entry, false))
        val open = book.advance(scan(entry), entry, true)
        assertEquals(999.95, open.balance, 0.000001)
        assertSame(open, open.advance(scan(entry), entry, true))
        val next = row(103.0, 120_000L)
        val done = open.advance(scan(next), null, true)
        assertNull(done.position)
        assertEquals(1002.8985, done.balance, 0.000001)
        assertEquals(1, done.trades)
        assertEquals(1, done.ratedTrades)
        assertEquals(done, PaperAccount.read(done.json()))
        val legacy = done.json().apply { remove("rated"); remove("wins") }
        assertEquals(0, PaperAccount.read(legacy).ratedTrades)
        assertEquals(done, PaperAccount.read(done.json()))
        assertEquals(open, PaperAccount.read(open.json()))
    }

    @Test fun disabledBotClosesVirtualShortAtNextValidQuote() {
        val entry = row(score = 0.3)
        val open = PaperAccount().advance(scan(entry), entry, true)
        assertEquals(-1, open.position!!.side)
        val later = row(99.8, 120_000L)
        val invalid = scan(later).copy(ok = false)
        assertSame(open, open.advance(invalid, null, false))
        assertNull(open.advance(scan(later), null, false).position)
    }
}
