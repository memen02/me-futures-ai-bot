package com.mefuturesaibot.app.ai.indicators

import com.mefuturesaibot.app.ai.Timeframe
import com.mefuturesaibot.app.ai.TimeframeHistory
import com.mefuturesaibot.app.exchange.Candle
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class MacdEngineTest {

    private val tolerance = 1e-9

    private fun history(
        prices: List<Double>,
        timeframe: Timeframe = Timeframe.M1
    ): TimeframeHistory {

        var time = 1704067200000L

        val candles = prices.map { price ->

            val value = BigDecimal.valueOf(price)

            val candle = Candle(
                openTime = time,
                open = value,
                high = value,
                low = value,
                close = value,
                volume = BigDecimal.ONE
            )

            time = timeframe.nextOpen(time)

            candle
        }

        return TimeframeHistory(
            symbol = "BTCUSDT",
            timeframe = timeframe,
            candles = candles,
            observedAt = time
        )
    }

    private fun calculate(
        h: TimeframeHistory,
        now: Long = h.observedAt
    ): MacdSnapshot? =
        MacdEngine.calculate(h, now)

    private fun series(
        prices: List<Double>
    ): List<MacdPoint> =
        requireNotNull(
            MacdEngine.series(prices)
        )

    private fun check(
        expected: Double,
        actual: Double?
    ) {
        assertEquals(
            expected,
            requireNotNull(actual),
            tolerance
        )
    }

    private fun checkPoint(
        point: MacdPoint,
        macd: Double,
        signal: Double,
        histogram: Double
    ) {
        check(macd, point.macd)
        check(signal, point.signal)
        check(histogram, point.histogram)
    }

    @Test
    fun flatPricesProduceZeroMacd() {

        val values = series(
            List(250) { 100.0 }
        )

        values.drop(33).forEach {
            checkPoint(
                it,
                0.0,
                0.0,
                0.0
            )
        }
    }

    @Test
    fun risingPricesMatchReference() {

        val values = series(
            List(250) { it + 1.0 }
        )

        values.drop(33).forEach {
            checkPoint(
                it,
                7.0,
                7.0,
                0.0
            )
        }
    }

    @Test
    fun fallingPricesMatchReference() {

        val values = series(
            List(250) { 300.0 - it }
        )

        values.drop(33).forEach {
            checkPoint(
                it,
                -7.0,
                -7.0,
                0.0
            )
        }
    }

    @Test
    fun priceJumpMatchesReference() {

        val prices =
            List(249) { 100.0 } + 200.0

        val expected = 2800.0 / 351.0

        checkPoint(
            series(prices).last(),
            expected,
            expected / 5.0,
            expected * 4.0 / 5.0
        )
    }

    @Test
    fun priceDropMatchesReference() {

        val prices =
            List(249) { 200.0 } + 100.0

        val expected = -2800.0 / 351.0

        checkPoint(
            series(prices).last(),
            expected,
            expected / 5.0,
            expected * 4.0 / 5.0
        )
    }

    @Test
    fun twentyFiveClosesAreInsufficient() {

        val values = series(
            List(25) { 100.0 }
        )

        assertEquals(25, values.size)

        assertTrue(
            values.all {
                it.macd == null &&
                it.signal == null &&
                it.histogram == null
            }
        )
    }

    @Test
    fun twentySixClosesEnableMacdLine() {

        val values = series(
            List(26) { it + 1.0 }
        )

        assertTrue(
            values.take(25).all {
                it.macd == null &&
                it.signal == null &&
                it.histogram == null
            }
        )

        val last = values.last()

        check(7.0, last.macd)

        assertNull(last.signal)
        assertNull(last.histogram)
    }

    @Test
    fun thirtyThreeClosesHaveNoSignal() {

        val values = series(
            List(33) { it + 1.0 }
        )

        check(7.0, values.last().macd)

        assertTrue(
            values.all {
                it.signal == null &&
                it.histogram == null
            }
        )
    }

    @Test
    fun thirtyFourClosesEnableFullMacd() {

        val values = series(
            List(34) { it + 1.0 }
        )

        assertTrue(
            values.take(33).all {
                it.signal == null &&
                it.histogram == null
            }
        )

        checkPoint(
            values[33],
            7.0,
            7.0,
            0.0
        )
    }

    @Test
    fun emptySeriesIsEmpty() {

        assertTrue(
            series(emptyList()).isEmpty()
        )
    }

    @Test
    fun invalidPricesAreRejected() {

        val invalid = listOf(
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
            Double.MIN_VALUE,
            0.0,
            -1.0
        )

        for (price in invalid) {

            assertNull(
                MacdEngine.series(
                    List(100) { 100.0 } +
                        price
                )
            )
        }
    }

    @Test
    fun histogramEqualsMacdMinusSignal() {

        val prices = List(499) {
            100.0 + (it * 17 % 41)
        }

        val values = series(prices)

        assertEquals(499, values.size)

        values.drop(33).forEach {

            val macd = requireNotNull(
                it.macd
            )

            val signal = requireNotNull(
                it.signal
            )

            val histogram = requireNotNull(
                it.histogram
            )

            assertTrue(macd.isFinite())
            assertTrue(signal.isFinite())
            assertTrue(histogram.isFinite())

            check(
                macd - signal,
                histogram
            )
        }
    }

    @Test
    fun positiveScalingScalesMacd() {

        val prices = List(250) {
            100.0 + (it * 17 % 41)
        }

        val original = series(prices)

        val scaled = series(
            prices.map { it * 10.0 }
        )

        for (i in 33 until prices.size) {

            checkPoint(
                scaled[i],
                requireNotNull(
                    original[i].macd
                ) * 10.0,
                requireNotNull(
                    original[i].signal
                ) * 10.0,
                requireNotNull(
                    original[i].histogram
                ) * 10.0
            )
        }
    }

    @Test
    fun futurePricesDoNotChangePastMacd() {

        val prices = List(250) {
            100.0 + (it * 17 % 41)
        }

        val full = series(prices)

        val prefix = series(
            prices.take(100)
        )

        assertEquals(
            prefix,
            full.take(100)
        )
    }

    @Test
    fun snapshotPreservesMetadata() {

        val h = history(
            List(250) { it + 1.0 }
        )

        val s = requireNotNull(
            calculate(h)
        )

        assertEquals(h.symbol, s.symbol)
        assertEquals(h.timeframe, s.timeframe)
        assertEquals(250, s.candleCount)

        assertEquals(
            h.observedAt - 1L,
            s.closedAtMs
        )

        check(250.0, s.close)
        check(7.0, s.macd)
        check(7.0, s.signal)
        check(0.0, s.histogram)

        assertTrue(s.allAvailable)
    }

    @Test
    fun insufficientSnapshotHasNullValues() {

        val h = history(
            List(25) { 100.0 }
        )

        val s = requireNotNull(
            calculate(h)
        )

        assertNull(s.macd)
        assertNull(s.signal)
        assertNull(s.histogram)

        assertFalse(s.allAvailable)
    }

    @Test
    fun partialSnapshotIsNotAllAvailable() {

        val h = history(
            List(33) { it + 1.0 }
        )

        val s = requireNotNull(
            calculate(h)
        )

        check(7.0, s.macd)

        assertNull(s.signal)
        assertNull(s.histogram)

        assertFalse(s.allAvailable)
    }

    @Test
    fun staleHistoryIsRejected() {

        val h = history(
            List(250) { 100.0 }
        )

        assertNull(
            calculate(
                h,
                h.timeframe.nextOpen(
                    h.observedAt
                )
            )
        )
    }

    @Test
    fun unclosedCandleIsRejected() {

        val h = history(
            List(250) { 100.0 }
        )

        assertNull(
            calculate(
                h.copy(
                    observedAt =
                        h.observedAt - 1L
                ),
                h.observedAt
            )
        )
    }

    @Test
    fun allFifteenTimeframesAreSupported() {

        assertEquals(
            15,
            Timeframe.entries.size
        )

        Timeframe.entries.forEach { frame ->

            val h = history(
                List(250) { it + 1.0 },
                frame
            )

            val s = requireNotNull(
                calculate(h)
            )

            assertEquals(
                frame,
                s.timeframe
            )

            assertEquals(
                h.observedAt - 1L,
                s.closedAtMs
            )

            check(7.0, s.macd)
            check(7.0, s.signal)
            check(0.0, s.histogram)

            assertTrue(s.allAvailable)
        }
    }
}
