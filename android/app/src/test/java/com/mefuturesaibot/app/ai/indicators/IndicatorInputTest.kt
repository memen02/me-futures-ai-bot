package com.mefuturesaibot.app.ai.indicators

import com.mefuturesaibot.app.ai.Timeframe
import com.mefuturesaibot.app.ai.TimeframeHistory
import com.mefuturesaibot.app.exchange.Candle
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class IndicatorInputTest {

    private fun history(
        count: Int = 250,
        timeframe: Timeframe = Timeframe.M1
    ): TimeframeHistory {

        var time = 1704067200000L

        val candles = List(count) { index ->

            val price = BigDecimal.valueOf(
                100L + index
            )

            val candle = Candle(
                openTime = time,
                open = price,
                high = price,
                low = price,
                close = price,
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

    private fun validate(
        h: TimeframeHistory,
        now: Long = h.observedAt
    ): IndicatorInput? =
        IndicatorInput.from(h, now)

    private fun replaceLast(
        h: TimeframeHistory,
        candle: Candle
    ): TimeframeHistory =
        h.copy(
            candles =
                h.candles.dropLast(1) + candle
        )

    @Test
    fun validHistoryPreservesMetadataAndOrder() {

        Timeframe.entries.forEach { frame ->

            val h = history(250, frame)

            val input = requireNotNull(
                validate(h)
            )

            assertEquals(h.symbol, input.symbol)
            assertEquals(frame, input.timeframe)
            assertEquals(250, input.candleCount)

            assertEquals(
                h.observedAt - 1L,
                input.closedAtMs
            )

            assertEquals(
                349.0,
                input.close,
                0.0
            )

            assertEquals(
                List(250) { 100.0 + it },
                input.closes
            )
        }
    }

    @Test
    fun historySizeBoundariesAreEnforced() {

        assertNull(validate(history(0)))

        assertNotNull(validate(history(1)))

        assertNotNull(validate(history(499)))

        assertNull(validate(history(500)))
    }

    @Test
    fun missingCandleIsRejected() {

        val h = history()

        val candles = h.candles
            .toMutableList()

        candles.removeAt(10)

        assertNull(
            validate(
                h.copy(candles = candles)
            )
        )
    }

    @Test
    fun duplicateAndReversedCandlesAreRejected() {

        val h = history()

        val candles = h.candles
            .toMutableList()

        candles[10] = candles[9]

        assertNull(
            validate(
                h.copy(candles = candles)
            )
        )

        assertNull(
            validate(
                h.copy(
                    candles =
                        h.candles.reversed()
                )
            )
        )
    }

    @Test
    fun unclosedAndMisalignedCandlesAreRejected() {

        val h = history()

        assertNull(
            validate(
                h.copy(
                    observedAt =
                        h.observedAt - 1L
                ),
                h.observedAt
            )
        )

        val last = h.candles.last()

        assertNull(
            validate(
                replaceLast(
                    h,
                    last.copy(
                        openTime =
                            last.openTime + 1L
                    )
                )
            )
        )
    }

    @Test
    fun invalidMetadataAndClockAreRejected() {

        val h = history()

        assertNull(
            validate(h.copy(symbol = " "))
        )

        assertNull(validate(h, 0L))

        assertNull(validate(h, -1L))

        assertNull(
            validate(
                h.copy(observedAt = 0L),
                h.observedAt
            )
        )

        assertNull(
            validate(
                h,
                h.observedAt - 1L
            )
        )
    }

    @Test
    fun freshnessBoundaryIsExact() {

        Timeframe.entries.forEach { frame ->

            val h = history(250, frame)

            val expires = frame.nextOpen(
                h.observedAt
            )

            assertNotNull(
                validate(h, h.observedAt)
            )

            assertNotNull(
                validate(h, expires - 1L)
            )

            assertNull(
                validate(h, expires)
            )

            assertNull(
                validate(h, expires + 1L)
            )
        }
    }

    @Test
    fun invalidOhlcAndVolumeAreRejected() {

        val h = history()

        val last = h.candles.last()

        val invalid = listOf(
            last.copy(low = BigDecimal.ZERO),
            last.copy(high = BigDecimal.ONE),
            last.copy(open = BigDecimal.ONE),
            last.copy(close = BigDecimal.ONE),
            last.copy(
                volume = BigDecimal("-1")
            )
        )

        invalid.forEach { candle ->

            assertNull(
                validate(
                    replaceLast(h, candle)
                )
            )
        }

        assertNotNull(
            validate(
                replaceLast(
                    h,
                    last.copy(
                        volume = BigDecimal.ZERO
                    )
                )
            )
        )
    }

    @Test
    fun unrepresentableClosePricesAreRejected() {

        val h = history()

        val last = h.candles.last()

        for (price in listOf(
            BigDecimal("1E400"),
            BigDecimal("1E-400"),
            BigDecimal("1E-310")
        )) {

            val candle = last.copy(
                open = price,
                high = price,
                low = price,
                close = price
            )

            assertNull(
                validate(
                    replaceLast(h, candle)
                )
            )
        }
    }

    @Test
    fun validatedPricesCannotBeMutated() {

        val original = history()

        val mutableCandles =
            original.candles.toMutableList()

        val h = original.copy(
            candles = mutableCandles
        )

        val input = requireNotNull(
            validate(h)
        )

        mutableCandles.clear()

        assertEquals(
            250,
            input.candleCount
        )

        assertEquals(
            100.0,
            input.closes.first(),
            0.0
        )

        try {

            val mutablePrices =
                input.closes as MutableList<Double>

            mutablePrices[0] = 999.0

            fail(
                "Validated prices must be immutable"
            )

        } catch (_: UnsupportedOperationException) {
            // Expected.
        }

        assertEquals(
            100.0,
            input.closes.first(),
            0.0
        )

        assertEquals(
            349.0,
            input.close,
            0.0
        )
    }
}
