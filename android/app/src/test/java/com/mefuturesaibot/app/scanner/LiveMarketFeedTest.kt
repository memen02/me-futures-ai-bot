package com.mefuturesaibot.app.scanner

import org.junit.Assert.*
import org.junit.Test

class LiveMarketFeedTest {

    private val valid = """
        {
          "e":"24hrTicker",
          "E":1795500000000,
          "s":"BTCUSDT",
          "c":"95000",
          "P":"2.5",
          "h":"97000",
          "l":"92000",
          "q":"100000000",
          "w":"94500"
        }
    """.trimIndent()

    @Test
    fun parsesRawTicker() {
        val ticker = LiveMarketFeed.parse("[$valid]")
            .getValue("BTCUSDT")

        assertEquals(95000.0, ticker.price, 0.0)
        assertEquals(2.5, ticker.change, 0.0)
        assertEquals(100000000.0, ticker.volume, 0.0)
    }

    @Test
    fun parsesCombinedStream() {
        val message =
            """{"stream":"!ticker@arr","data":[$valid]}"""

        assertTrue(
            LiveMarketFeed.parse(message)
                .containsKey("BTCUSDT")
        )
    }

    @Test
    fun rejectsMalformedJson() {
        assertTrue(
            LiveMarketFeed.parse("invalid").isEmpty()
        )
    }

    @Test
    fun rejectsZeroPrice() {
        val invalid = valid.replace("95000", "0")
        assertTrue(
            LiveMarketFeed.parse("[$invalid]").isEmpty()
        )
    }

    @Test
    fun rejectsNegativeVolume() {
        val invalid = valid.replace("100000000", "-1")
        assertTrue(
            LiveMarketFeed.parse("[$invalid]").isEmpty()
        )
    }

    @Test
    fun rejectsUnknownEvent() {
        val invalid = valid.replace(
            "24hrTicker", "unknown"
        )

        assertTrue(
            LiveMarketFeed.parse("[$invalid]").isEmpty()
        )
    }

    @Test
    fun keepsNewestTicker() {
        val older = valid
            .replace("1795500000000", "1795499999000")
            .replace("95000", "94000")

        val ticker = LiveMarketFeed
            .parse("[$valid,$older]")
            .getValue("BTCUSDT")

        assertEquals(95000.0, ticker.price, 0.0)
    }

    @Test
    fun rejectsEmptyArray() {
        assertTrue(
            LiveMarketFeed.parse("[]").isEmpty()
        )
    }
}
