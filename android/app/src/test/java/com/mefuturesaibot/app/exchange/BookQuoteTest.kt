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
        val clock = ExchangeClock().apply { observe(quote.eventTimeMs, quote.receivedElapsedMs) }
        assertFalse(quote.isStale(10_049, clock))
        assertTrue(quote.isStale(10_050, clock))
        assertTrue(quote.copy(disconnected = true).isStale(51, clock))
        // Pesan lama tiba lagi setelah sepuluh detik, dengan waktu terima baru.
        clock.observe(quote.eventTimeMs, 10_050)
        assertTrue(quote.copy(receivedElapsedMs = 10_050).isStale(10_050, clock))
    }

    @Test fun phoneClockOffsetDoesNotChangeQuoteAge() {
        // Dua pembacaan baru dengan waktu bursa berbeda 30 detik dari epoch
        // nominal HP. Umur data mengikuti durasi sejak diterima di kedua kasus.
        val nominalPhoneEpoch = 1_800_000_000_000L
        val elapsed = 75_000L
        for (offset in listOf(-30_000L, 30_000L)) {
            val exchangeMs = nominalPhoneEpoch + offset
            val clock = ExchangeClock().apply { observe(exchangeMs, elapsed) }
            val quote = BookQuote("1", "2", exchangeMs, elapsed)
            assertFalse(quote.isStale(elapsed + 9999, clock))
            assertTrue(quote.isStale(elapsed + 10_000, clock))
            assertEquals(exchangeMs + 10_000, clock.at(elapsed + 10_000))
        }
    }
}
