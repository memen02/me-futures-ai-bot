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

    @Test fun riskAndLedgerUseNetRewardAndBookFundingOnce() {
        val entry = row()
        val q = Quote(99.99,100.0,60_000)
        val plan = RiskPlan.build(1,q,Levels(99.0,104.0),1000.0,60_000)
        assertTrue(plan.quantity in 4.0..5.0)
        assertThrows(IllegalArgumentException::class.java) { RiskPlan.build(1,q,Levels(99.0,102.0),1000.0,60_000) }
        assertThrows(IllegalArgumentException::class.java) { RiskPlan.build(1,q.copy(bid=99.0),Levels(99.0,105.0),1000.0,60_000) }
        val initial = PaperAccount()
        val open = initial.advance(scan(entry),"ETHUSDT",plan,true,q)
        assertEquals(initial.balance - plan.quantity * plan.entry * RiskPlan.FEE,open.balance,0.000001)
        assertEquals(open,PaperAccount.read(open.json()))
        val funding = listOf(Funding(90_000,0.001,100.0))
        val middle = open.advance(scan(row(time=120_000)),null,null,true,q.copy(at=120_000),funding)
        val done = middle.advance(scan(row(time=180_000)),null,null,true,Quote(104.1,104.11,180_000),funding)
        val exit = 104.1 * (1 - RiskPlan.SLIP)
        val net = plan.quantity * (exit-plan.entry - RiskPlan.FEE*(exit+plan.entry) - 0.1)
        assertNull(done.position)
        assertEquals(1000.0 + net,done.balance,0.000001)
        assertEquals(net,done.profits,0.000001)
        assertSame(done,done.advance(scan(row(time=180_000)),null,null,true,q))
        assertEquals(done,PaperAccount.read(done.json()))
    }

    @Test fun dayLockAndMigrationAndShortExit() {
        val q = Quote(99.99,100.0,60_000)
        val plan = RiskPlan.build(-1,q,Levels(96.0,101.0),1000.0,60_000)
        val account = PaperAccount(balance=980.0,day=0,dayStart=1000.0)
        assertSame(account,account.advance(scan(row()),contract.symbol,plan,true,q))
        assertFalse(account.locked(86_400_000))
        val open = PaperAccount().advance(scan(row()),contract.symbol,plan,true,q)
        assertSame(open,open.advance(scan(row(time=120_000)),null,null,false,q))
        val done = open.advance(scan(row(time=120_000)),null,null,false,q.copy(at=120_000))
        assertNull(done.position)
        assertEquals(1,done.measuredLosses)
        val json = open.json()
        json.getJSONObject("position").apply { remove("qty");remove("stop");remove("target") }
        val legacy = PaperAccount.read(json).position!!
        assertEquals(100.0,legacy.quantity * legacy.entry,0.000001)
    }
}
