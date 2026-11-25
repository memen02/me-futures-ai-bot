package com.mefuturesaibot.app.ai.indicators

import com.mefuturesaibot.app.ai.Timeframe
import com.mefuturesaibot.app.ai.TimeframeHistory
import com.mefuturesaibot.app.exchange.Candle
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class MovingAverageEngineTest {

    private val tolerance = 1e-9

    private fun history(
        count: Int = 250,
        timeframe: Timeframe = Timeframe.M1,
        price: (Int) -> Double = { 100.0 }
    ): TimeframeHistory {

        // Monday, 1 January 2024, UTC.
        var time = 1704067200000L

        val candles = List(count) { index ->

            val value = BigDecimal.valueOf(
                price(index)
            )

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
    ): MovingAverageSnapshot? =
        MovingAverageEngine.calculate(h, now)

    private fun result(
        h: TimeframeHistory
    ): MovingAverageSnapshot =
        requireNotNull(calculate(h))

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

    private fun averages(
        s: MovingAverageSnapshot
    ): List<Double?> =
        listOf(
            s.ema9,
            s.ema20,
            s.ema50,
            s.ema100,
            s.ema200,
            s.sma20,
            s.sma50,
            s.sma200
        )

    private fun replaceLast(
        h: TimeframeHistory,
        candle: Candle
    ): TimeframeHistory =
        h.copy(
            candles =
                h.candles.dropLast(1) + candle
        )

    @Test
    fun constantPricesProduceEqualAverages() {
        val s = result(history())

        averages(s).forEach {
            check(100.0, it)
        }

        assertTrue(s.allAvailable)
    }

    @Test
    fun risingPricesMatchReference() {
        val s = result(
            history { it + 1.0 }
        )

        check(250.0, s.close)
        check(246.0, s.ema9)
        check(240.5, s.ema20)
        check(225.5, s.ema50)
        check(200.5, s.ema100)
        check(150.5, s.ema200)

        check(240.5, s.sma20)
        check(225.5, s.sma50)
        check(150.5, s.sma200)
    }

    @Test
    fun fallingPricesMatchReference() {
        val s = result(
            history { 300.0 - it }
        )

        check(51.0, s.close)
        check(55.0, s.ema9)
        check(60.5, s.ema20)
        check(75.5, s.ema50)
        check(100.5, s.ema100)
        check(150.5, s.ema200)

        check(60.5, s.sma20)
        check(75.5, s.sma50)
        check(150.5, s.sma200)
    }

    @Test
    fun priceJumpMatchesReference() {
        val s = result(
            history {
                if (it == 249) 200.0
                else 100.0
            }
        )

        check(120.0, s.ema9)
        check(100.0 + 200.0 / 21.0, s.ema20)
        check(105.0, s.sma20)
        check(102.0, s.sma50)
        check(100.5, s.sma200)
    }

    @Test
    fun metadataIsPreserved() {
        val h = history()
        val s = result(h)

        assertEquals(h.symbol, s.symbol)
        assertEquals(h.timeframe, s.timeframe)
        assertEquals(250, s.candleCount)
        assertEquals(
            h.observedAt - 1L,
            s.closedAtMs
        )
    }

    @Test
    fun eightCandlesHaveNoAverages() {
        val s = result(history(8))

        assertTrue(
            averages(s).all { it == null }
        )

        assertFalse(s.allAvailable)
    }

    @Test
    fun nineCandlesEnableEma9() {
        val s = result(history(9))

        check(100.0, s.ema9)
        assertNull(s.ema20)
        assertNull(s.sma20)
        assertFalse(s.allAvailable)
    }

    @Test
    fun twentyCandlesEnableShortAverages() {
        val s = result(history(20))

        check(100.0, s.ema9)
        check(100.0, s.ema20)
        check(100.0, s.sma20)

        assertNull(s.ema50)
        assertNull(s.sma50)
    }

    @Test
    fun fiftyCandlesEnableMediumAverages() {
        val s = result(history(50))

        check(100.0, s.ema50)
        check(100.0, s.sma50)

        assertNull(s.ema100)
        assertNull(s.ema200)
    }

    @Test
    fun hundredCandlesEnableEma100() {
        val s = result(history(100))

        check(100.0, s.ema100)

        assertNull(s.ema200)
        assertNull(s.sma200)
    }

    @Test
    fun incompleteLongHistoryIsNotAllAvailable() {
        val s = result(history(199))

        assertNull(s.ema200)
        assertNull(s.sma200)
        assertFalse(s.allAvailable)
    }

    @Test
    fun twoHundredCandlesEnableAllAverages() {
        val s = result(history(200))

        assertTrue(s.allAvailable)
    }

    @Test
    fun maximumHistoryIsAccepted() {
        val s = result(history(499))

        assertEquals(499, s.candleCount)
        assertTrue(s.allAvailable)
    }

    @Test
    fun excessiveHistoryIsRejected() {
        assertNull(calculate(history(500)))
    }

    @Test
    fun emptyHistoryIsRejected() {
        assertNull(calculate(history(0)))
    }

    @Test
    fun blankSymbolIsRejected() {
        assertNull(
            calculate(
                history().copy(symbol = " ")
            )
        )
    }

    @Test
    fun invalidExchangeTimeIsRejected() {
        assertNull(calculate(history(), 0L))
    }

    @Test
    fun invalidObservationTimeIsRejected() {
        assertNull(
            calculate(
                history().copy(observedAt = 0L),
                1704067200000L
            )
        )
    }

    @Test
    fun futureObservationIsRejected() {
        val h = history()

        assertNull(
            calculate(h, h.observedAt - 1L)
        )
    }

    @Test
    fun staleHistoryIsRejected() {
        val h = history()

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
    fun historyRemainsFreshBeforeNextClose() {
        val h = history()

        assertNotNull(
            calculate(
                h,
                h.timeframe.nextOpen(
                    h.observedAt
                ) - 1L
            )
        )
    }

    @Test
    fun unclosedCandleIsRejected() {
        val h = history()

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
    fun missingCandleIsRejected() {
        val h = history()

        assertNull(
            calculate(
                h.copy(
                    candles =
                        h.candles.filterIndexed {
                            index, _ -> index != 10
                        }
                )
            )
        )
    }

    @Test
    fun duplicateCandleIsRejected() {
        val h = history()

        val bars = h.candles.toMutableList()

        bars[10] = bars[9]

        assertNull(
            calculate(h.copy(candles = bars))
        )
    }

    @Test
    fun reversedHistoryIsRejected() {
        val h = history()

        assertNull(
            calculate(
                h.copy(
                    candles = h.candles.reversed()
                )
            )
        )
    }

    @Test
    fun misalignedCandleIsRejected() {
        val h = history()

        val bad = h.candles.last().copy(
            openTime =
                h.candles.last().openTime + 1L
        )

        assertNull(
            calculate(replaceLast(h, bad))
        )
    }

    @Test
    fun invalidOhlcIsRejected() {
        val h = history()
        val last = h.candles.last()

        val invalid = listOf(
            last.copy(low = BigDecimal.ZERO),
            last.copy(high = BigDecimal.ONE),
            last.copy(close = BigDecimal.ONE),
            last.copy(open = BigDecimal.ONE)
        )

        invalid.forEach {
            assertNull(
                calculate(replaceLast(h, it))
            )
        }
    }

    @Test
    fun negativeVolumeIsRejected() {
        val h = history()

        val bad = h.candles.last().copy(
            volume = BigDecimal.valueOf(-1L)
        )

        assertNull(
            calculate(replaceLast(h, bad))
        )
    }

    @Test
    fun unrepresentablePricesAreRejected() {
        val h = history()

        for (price in listOf("1E400", "1E-400")) {

            val value = BigDecimal(price)

            val bad = h.candles.last().copy(
                open = value,
                high = value,
                low = value,
                close = value
            )

            assertNull(
                calculate(replaceLast(h, bad))
            )
        }
    }

    @Test
    fun allFifteenTimeframesAreSupported() {
        assertEquals(15, Timeframe.entries.size)

        Timeframe.entries.forEach { timeframe ->

            val h = history(
                count = 250,
                timeframe = timeframe
            )

            val s = result(h)

            assertEquals(
                timeframe,
                s.timeframe
            )

            assertTrue(s.allAvailable)

            averages(s).forEach {
                check(100.0, it)
            }

            assertEquals(
                h.observedAt - 1L,
                s.closedAtMs
            )
        }
    }
}
