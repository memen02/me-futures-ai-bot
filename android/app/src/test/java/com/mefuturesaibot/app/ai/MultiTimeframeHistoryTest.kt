package com.mefuturesaibot.app.ai

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultiTimeframeHistoryTest {

    private fun ms(value: String): Long =
        Instant.parse(value).toEpochMilli()

    private fun row(
        tf: Timeframe,
        open: Long,
        price: String = "100",
        volume: String = "500"
    ): JSONArray = JSONArray()
        .put(open)
        .put(price)
        .put("110")
        .put("90")
        .put("105")
        .put(volume)
        .put(tf.nextOpen(open) - 1L)

    private fun rejected(block: () -> Unit) {
        try {
            block()
            fail("Data tidak valid seharusnya ditolak")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun supportsAll15Timeframes() {
        val expected = listOf(
            "1m", "3m", "5m", "15m", "30m",
            "1h", "2h", "4h", "6h", "8h",
            "12h", "1d", "3d", "1w", "1M"
        )

        assertEquals(
            expected,
            Timeframe.entries.map { it.api }
        )

        expected.forEach {
            assertNotNull(Timeframe.fromApi(it))
        }

        assertNull(Timeframe.fromApi("2m"))
    }

    @Test
    fun allTimeframesExcludeOpenCandle() {
        val start = ms("2024-01-01T00:00:00Z")

        for (tf in Timeframe.entries) {
            assertTrue(tf.validOpen(start))

            val second = tf.nextOpen(start)

            val body = JSONArray()
                .put(row(tf, start))
                .put(row(tf, second))

            val history = MultiTimeframeHistory.parse(
                "BTCUSDT", tf, body.toString(), second
            )

            assertEquals(tf.api, 1, history.candles.size)
            assertEquals(start, history.lastOpen)
            assertTrue(history.fresh(second))
            assertFalse(
                history.fresh(tf.nextOpen(second))
            )
        }
    }

    @Test
    fun leapYearFebruary() {
        val feb = ms("2024-02-01T00:00:00Z")
        val march = ms("2024-03-01T00:00:00Z")

        assertEquals(
            march,
            Timeframe.MN1.nextOpen(feb)
        )

        assertEquals(
            29L * 86_400_000L,
            march - feb
        )
    }

    @Test
    fun monthAndYearTransition() {
        assertEquals(
            ms("2027-01-01T00:00:00Z"),
            Timeframe.MN1.nextOpen(
                ms("2026-12-01T00:00:00Z")
            )
        )
    }

    @Test
    fun weeklyAlignment() {
        assertTrue(
            Timeframe.W1.validOpen(
                ms("2024-01-01T00:00:00Z")
            )
        )

        assertFalse(
            Timeframe.W1.validOpen(
                ms("2024-01-02T00:00:00Z")
            )
        )
    }

    @Test
    fun rejectsDuplicateCandles() {
        val tf = Timeframe.M1
        val start = 60_000L

        val body = JSONArray()
            .put(row(tf, start))
            .put(row(tf, start))

        rejected {
            MultiTimeframeHistory.parse(
                "BTCUSDT", tf,
                body.toString(), 180_000L
            )
        }
    }

    @Test
    fun rejectsMissingCandles() {
        val tf = Timeframe.M1

        val body = JSONArray()
            .put(row(tf, 60_000L))
            .put(row(tf, 180_000L))

        rejected {
            MultiTimeframeHistory.parse(
                "BTCUSDT", tf,
                body.toString(), 240_000L
            )
        }
    }

    @Test
    fun rejectsFutureCandles() {
        val tf = Timeframe.M1

        val body = JSONArray()
            .put(row(tf, 180_000L))

        rejected {
            MultiTimeframeHistory.parse(
                "BTCUSDT", tf,
                body.toString(), 120_000L
            )
        }
    }

    @Test
    fun rejectsInvalidOhlc() {
        val tf = Timeframe.M1

        val body = JSONArray()
            .put(row(tf, 60_000L, price = "150"))

        rejected {
            MultiTimeframeHistory.parse(
                "BTCUSDT", tf,
                body.toString(), 120_000L
            )
        }
    }

    @Test
    fun rejectsNegativeVolume() {
        val tf = Timeframe.M1

        val body = JSONArray()
            .put(row(tf, 60_000L, volume = "-1"))

        rejected {
            MultiTimeframeHistory.parse(
                "BTCUSDT", tf,
                body.toString(), 120_000L
            )
        }
    }

    @Test
    fun shortHistoryIsNotReady() {
        val tf = Timeframe.M1

        val body = JSONArray()
            .put(row(tf, 60_000L))

        val history = MultiTimeframeHistory.parse(
            "NEWUSDT", tf,
            body.toString(), 120_000L
        )

        assertTrue(history.ready(1, 120_000L))
        assertFalse(history.ready(200, 120_000L))
    }

    @Test
    fun staleHistoryIsNotReady() {
        val tf = Timeframe.M1

        val body = JSONArray()
            .put(row(tf, 60_000L))

        val history = MultiTimeframeHistory.parse(
            "BTCUSDT", tf,
            body.toString(), 120_000L
        )

        assertFalse(history.ready(1, 180_000L))
        assertFalse(history.fresh(119_999L))
    }

    @Test
    fun rejectsWrongCloseTime() {
        val tf = Timeframe.M1
        val candle = row(tf, 60_000L)
        candle.put(6, 119_000L)

        rejected {
            MultiTimeframeHistory.parse(
                "BTCUSDT", tf,
                JSONArray().put(candle).toString(),
                120_000L
            )
        }
    }

    @Test
    fun rejectsEmptyResponse() {
        rejected {
            MultiTimeframeHistory.parse(
                "BTCUSDT", Timeframe.M1,
                "[]", 120_000L
            )
        }
    }

    @Test
    fun openOnlyHistoryIsNotReady() {
        val tf = Timeframe.M1
        val body = JSONArray()
            .put(row(tf, 120_000L))

        val history = MultiTimeframeHistory.parse(
            "NEWUSDT", tf,
            body.toString(), 130_000L
        )

        assertTrue(history.candles.isEmpty())
        assertFalse(history.fresh(130_000L))
        assertFalse(history.ready(1, 130_000L))
    }

    @Test
    fun observedBinanceThreeDaySequence() {
        val tf = Timeframe.D3
        val first = ms("2026-11-13T00:00:00Z")
        val second = ms("2026-11-16T00:00:00Z")
        val third = ms("2026-11-19T00:00:00Z")

        assertTrue(tf.validOpen(first))
        assertEquals(second, tf.nextOpen(first))
        assertEquals(third, tf.nextOpen(second))
    }
}
