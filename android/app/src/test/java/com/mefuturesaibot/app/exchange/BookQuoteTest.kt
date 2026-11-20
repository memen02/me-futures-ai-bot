package com.mefuturesaibot.app.exchange

import org.junit.Assert.*
import org.junit.Test

class BookQuoteTest {
    private val ticker = """{"e":"bookTicker","s":"BTCUSDT","b":"100.10","a":"100.20","E":1000}"""

    @Test
    fun readsBestPricesFromCombinedStreamWithoutRounding() {
        val quote = BookQuote.parse("""{"stream":"btcusdt@bookTicker","data":$ticker}""", 50)
        assertEquals(BookQuote("100.10", "100.20", 1000, 50), quote)
    }

    @Test
    fun invalidUpdatesDoNotRefreshLastGoodPrice() {
        for (message in listOf("{", "{}", ticker.replace("BTCUSDT", "ETHUSDT"),
                ticker.replace("100.10", "invalid"), ticker.replace("100.20", "99.99"))) {
            assertNull(BookQuote.parse(message, 50))
        }
    }

    @Test
    fun expiresOnDisconnectOrTenSecondsOfSilenceOrDelayedExchangeData() {
        val quote = requireNotNull(BookQuote.parse(ticker, 50))
        assertFalse(quote.isStale(10_049, 10_999))
        assertTrue(quote.isStale(10_050, 1_001))
        assertTrue(quote.isStale(51, 11_000))
        assertTrue(quote.copy(disconnected = true).isStale(51, 1_001))
    }
}
