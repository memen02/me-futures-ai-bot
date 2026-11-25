package com.mefuturesaibot.app.ai.indicators

import com.mefuturesaibot.app.ai.Timeframe
import com.mefuturesaibot.app.ai.TimeframeHistory
import com.mefuturesaibot.app.exchange.Candle
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class RsiEngineTest {

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
    ): RsiSnapshot? =
        RsiEngine.calculate(h, now)

    private fun series(
        prices: List<Double>,
        period: Int = 14
    ): List<Double?> =
        requireNotNull(
            RsiEngine.series(prices, period)
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

    private fun balancedPrices(): List<Double> =
        List(15) {
            if (it % 2 == 0) 100.0
            else 101.0
        }

    @Test
    fun risingPricesProduceRsi100() {

        val values = series(
            List(250) { it + 1.0 }
        )

        values.drop(14).forEach {
            check(100.0, it)
        }
    }

    @Test
    fun fallingPricesProduceRsiZero() {

        val values = series(
            List(250) { 300.0 - it }
        )

        values.drop(14).forEach {
            check(0.0, it)
        }
    }

    @Test
    fun flatPricesProduceNeutralRsi() {

        val values = series(
            List(250) { 100.0 }
        )

        values.drop(14).forEach {
            check(50.0, it)
        }
    }

    @Test
    fun balancedSeedProducesRsi50() {

        check(
            50.0,
            series(balancedPrices()).last()
        )
    }

    @Test
    fun wilderGainUpdateMatchesReference() {

        val prices =
            balancedPrices() + 102.0

        val values = series(prices)

        check(50.0, values[14])

        check(
            1700.0 / 30.0,
            values[15]
        )
    }

    @Test
    fun wilderLossUpdateMatchesReference() {

        val prices =
            balancedPrices() + 98.0

        val values = series(prices)

        check(50.0, values[14])

        check(
            1300.0 / 30.0,
            values[15]
        )
    }

    @Test
    fun fourteenClosesAreInsufficient() {

        val values = series(
            List(14) { 100.0 }
        )

        assertEquals(14, values.size)

        assertTrue(
            values.all { it == null }
        )
    }

    @Test
    fun fifteenClosesEnableFirstRsi() {

        val values = series(
            List(15) { 100.0 }
        )

        assertTrue(
            values.take(14).all {
                it == null
            }
        )

        check(50.0, values[14])
    }

    @Test
    fun emptySeriesIsEmpty() {

        assertTrue(
            series(emptyList()).isEmpty()
        )
    }

    @Test
    fun invalidPeriodsAreRejected() {

        val prices = balancedPrices()

        for (period in listOf(0, -1, -14)) {

            assertNull(
                RsiEngine.series(
                    prices,
                    period
                )
            )
        }
    }

    @Test
    fun periodOneIsSupported() {

        val values = series(
            listOf(
                100.0,
                101.0,
                99.0,
                99.0
            ),
            period = 1
        )

        assertNull(values[0])

        check(100.0, values[1])
        check(0.0, values[2])
        check(50.0, values[3])
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
                RsiEngine.series(
                    balancedPrices() + price
                )
            )
        }
    }

    @Test
    fun mixedPricesStayWithinRsiRange() {

        val prices = List(499) {
            100.0 + (it * 17 % 41)
        }

        val values = series(prices)

        assertEquals(499, values.size)

        values.drop(14).forEach {

            val value = requireNotNull(it)

            assertTrue(value.isFinite())

            assertTrue(
                value in 0.0..100.0
            )
        }
    }

    @Test
    fun positivePriceScalingPreservesRsi() {

        val prices = List(250) {
            100.0 + (it * 17 % 41)
        }

        val original = series(prices)

        val scaled = series(
            prices.map { it * 10.0 }
        )

        for (i in 14 until prices.size) {

            check(
                requireNotNull(original[i]),
                scaled[i]
            )
        }
    }

    @Test
    fun futurePricesDoNotChangePastRsi() {

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
            balancedPrices()
        )

        val s = requireNotNull(
            calculate(h)
        )

        assertEquals(h.symbol, s.symbol)
        assertEquals(h.timeframe, s.timeframe)
        assertEquals(15, s.candleCount)

        assertEquals(
            h.observedAt - 1L,
            s.closedAtMs
        )

        check(100.0, s.close)
        check(50.0, s.rsi14)
    }

    @Test
    fun insufficientSnapshotHasNullRsi() {

        val h = history(
            List(14) { 100.0 }
        )

        val s = requireNotNull(
            calculate(h)
        )

        assertNull(s.rsi14)
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
                List(250) { 100.0 },
                frame
            )

            val s = requireNotNull(
                calculate(h)
            )

            assertEquals(
                frame,
                s.timeframe
            )

            check(50.0, s.rsi14)

            assertEquals(
                h.observedAt - 1L,
                s.closedAtMs
            )
        }
    }
}
