package com.mefuturesaibot.app.scanner

import org.junit.Assert.*
import org.junit.Test

class DetailTickerTest {

    private val symbol = "BTCUSDT"

    private val ticker = Ticker(
        price = 100.0,
        change = 2.0,
        high = 120.0,
        low = 80.0,
        volume = 1000000.0,
        average = 99.0,
        time = 200000L
    )

    private fun snapshot(
        value: Ticker? = ticker,
        name: String = symbol
    ) = Scan(
        rows = listOf(
            MarketRow(
                Contract(name, "USDT"),
                value,
                null
            )
        ),
        time = 200000L,
        received = 1000L,
        ok = true
    )

    @Test
    fun acceptsFreshTicker() {
        assertEquals(
            ticker,
            detailTicker(
                snapshot(), symbol, 1000L, null
            )
        )
    }

    @Test
    fun acceptsNewerTicker() {
        val newer = ticker.copy(
            price = 101.0,
            time = 201000L
        )

        assertEquals(
            newer,
            detailTicker(
                snapshot(newer),
                symbol,
                2000L,
                ticker
            )
        )
    }

    @Test
    fun rejectsDifferentSymbol() {
        assertNull(
            detailTicker(
                snapshot(name = "ETHUSDT"),
                symbol,
                1000L,
                null
            )
        )
    }

    @Test
    fun rejectsExpiredSnapshot() {
        assertNull(
            detailTicker(
                snapshot(),
                symbol,
                121001L,
                ticker
            )
        )
    }

    @Test
    fun rejectsOlderTicker() {
        val older = ticker.copy(
            price = 99.0,
            time = 199000L
        )

        assertNull(
            detailTicker(
                snapshot(older),
                symbol,
                1000L,
                ticker
            )
        )
    }

    @Test
    fun rejectsInvalidPrices() {
        for (price in listOf(
            0.0, -1.0, Double.NaN,
            Double.POSITIVE_INFINITY
        )) {
            assertNull(
                detailTicker(
                    snapshot(ticker.copy(price = price)),
                    symbol,
                    1000L,
                    null
                )
            )
        }
    }

    @Test
    fun rejectsMissingTicker() {
        assertNull(
            detailTicker(
                snapshot(null),
                symbol,
                1000L,
                ticker
            )
        )
    }

    @Test
    fun rejectsInvalidTickerTime() {
        for (time in listOf(
            79999L, 205001L
        )) {
            assertNull(
                detailTicker(
                    snapshot(ticker.copy(time = time)),
                    symbol,
                    1000L,
                    null
                )
            )
        }
    }
}
