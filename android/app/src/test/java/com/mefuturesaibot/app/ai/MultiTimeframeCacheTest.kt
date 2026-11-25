package com.mefuturesaibot.app.ai

import com.mefuturesaibot.app.exchange.Candle
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class MultiTimeframeCacheTest {

    private fun history(
        symbol: String = "BTCUSDT",
        tf: Timeframe = Timeframe.M1,
        open: Long = 60_000L,
        observed: Long = tf.nextOpen(open)
    ): TimeframeHistory {

        val candle = Candle(
            open,
            BigDecimal("100"),
            BigDecimal("110"),
            BigDecimal("90"),
            BigDecimal("105"),
            BigDecimal("500")
        )

        return TimeframeHistory(
            symbol,
            tf,
            listOf(candle),
            observed
        )
    }

    @Test
    fun storesFreshHistory() {
        val cache = MultiTimeframeCache()

        assertTrue(cache.put(history()))

        assertNotNull(
            cache.get(
                "BTCUSDT",
                Timeframe.M1,
                120_000L
            )
        )

        assertEquals(1, cache.symbolCount())
        assertEquals(1, cache.candleCount())
    }

    @Test
    fun expiredHistoryIsNotReturned() {
        val cache = MultiTimeframeCache()

        cache.put(history())

        assertNull(
            cache.get(
                "BTCUSDT",
                Timeframe.M1,
                180_000L
            )
        )
    }

    @Test
    fun rejectsEmptyHistory() {
        val cache = MultiTimeframeCache()

        assertFalse(
            cache.put(
                history().copy(candles = emptyList())
            )
        )

        assertEquals(0, cache.symbolCount())
    }

    @Test
    fun rejectsAlreadyStaleHistory() {
        val cache = MultiTimeframeCache()

        assertFalse(
            cache.put(
                history(observed = 180_000L)
            )
        )
    }

    @Test
    fun rejectsOlderObservation() {
        val cache = MultiTimeframeCache()

        assertTrue(
            cache.put(
                history(observed = 130_000L)
            )
        )

        assertFalse(
            cache.put(
                history(observed = 120_000L)
            )
        )
    }

    @Test
    fun replacesHistoryWithNewerCandle() {
        val cache = MultiTimeframeCache()

        cache.put(history())

        assertTrue(
            cache.put(
                history(open = 120_000L)
            )
        )

        val result = cache.get(
            "BTCUSDT",
            Timeframe.M1,
            180_000L
        )

        assertEquals(120_000L, result?.lastOpen)
        assertEquals(1, cache.candleCount())
    }

    @Test
    fun evictsLeastRecentlyUsedSymbol() {
        val cache = MultiTimeframeCache(2)

        cache.put(history("BTCUSDT"))
        cache.put(history("ETHUSDT"))

        cache.get(
            "BTCUSDT",
            Timeframe.M1,
            120_000L
        )

        cache.put(history("SOLUSDT"))

        assertEquals(2, cache.symbolCount())

        assertNull(
            cache.get(
                "ETHUSDT",
                Timeframe.M1,
                120_000L
            )
        )

        assertNotNull(
            cache.get(
                "BTCUSDT",
                Timeframe.M1,
                120_000L
            )
        )
    }

    @Test
    fun reportsMissingTimeframes() {
        val cache = MultiTimeframeCache()

        cache.put(history())

        val missing = cache.missing(
            "BTCUSDT",
            120_000L
        )

        assertEquals(14, missing.size)
        assertFalse(Timeframe.M1 in missing)
    }

    @Test
    fun separatesTimeframes() {
        val cache = MultiTimeframeCache()

        cache.put(history(open = 240_000L))

        cache.put(
            history(
                tf = Timeframe.M5,
                open = 0L
            )
        )

        val snapshot = cache.snapshot(
            "BTCUSDT",
            300_000L
        )

        assertEquals(2, snapshot.size)
        assertTrue(Timeframe.M1 in snapshot)
        assertTrue(Timeframe.M5 in snapshot)
    }

    @Test
    fun invalidateRemovesHistory() {
        val cache = MultiTimeframeCache()

        cache.put(history())

        cache.invalidate(
            "BTCUSDT",
            Timeframe.M1
        )

        assertEquals(0, cache.symbolCount())
        assertEquals(0, cache.candleCount())
    }

    @Test
    fun clearRemovesEverything() {
        val cache = MultiTimeframeCache()

        cache.put(history("BTCUSDT"))
        cache.put(history("ETHUSDT"))

        cache.clear()

        assertEquals(0, cache.symbolCount())
        assertEquals(0, cache.candleCount())
    }
}
