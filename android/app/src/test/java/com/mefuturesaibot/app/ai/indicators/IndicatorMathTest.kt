package com.mefuturesaibot.app.ai.indicators

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.sqrt

class IndicatorMathTest {

    private val tolerance = 1e-10

    private val values =
        listOf(1.0, 2.0, 3.0, 4.0, 5.0)

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

    @Test
    fun smaUsesLatestWindow() {
        check(
            4.0,
            IndicatorMath.sma(values, 3)
        )
    }

    @Test
    fun smaUsesEntireWindow() {
        check(
            3.0,
            IndicatorMath.sma(values, 5)
        )
    }

    @Test
    fun emaUsesSmaSeed() {
        check(
            2.0,
            IndicatorMath.ema(
                listOf(1.0, 2.0, 3.0),
                3
            )
        )
    }

    @Test
    fun emaMatchesReference() {
        check(
            4.0,
            IndicatorMath.ema(values, 3)
        )
    }

    @Test
    fun emaHandlesNonlinearPrices() {
        check(
            31.0 / 3.0,
            IndicatorMath.ema(
                listOf(2.0, 4.0, 8.0, 16.0),
                3
            )
        )
    }

    @Test
    fun rmaUsesSmaSeed() {
        check(
            2.0,
            IndicatorMath.rma(
                listOf(1.0, 2.0, 3.0),
                3
            )
        )
    }

    @Test
    fun rmaMatchesReference() {
        check(
            31.0 / 9.0,
            IndicatorMath.rma(values, 3)
        )
    }

    @Test
    fun standardDeviationMatchesPopulationReference() {
        check(
            sqrt(2.0),
            IndicatorMath.standardDeviation(
                values,
                5
            )
        )
    }

    @Test
    fun standardDeviationUsesLatestWindow() {
        check(
            sqrt(2.0 / 3.0),
            IndicatorMath.standardDeviation(
                values,
                3
            )
        )
    }

    @Test
    fun constantPricesRemainConstant() {
        val flat = List(50) { 100.0 }

        check(
            100.0,
            IndicatorMath.sma(flat, 20)
        )

        check(
            100.0,
            IndicatorMath.ema(flat, 20)
        )

        check(
            100.0,
            IndicatorMath.rma(flat, 20)
        )

        check(
            0.0,
            IndicatorMath.standardDeviation(
                flat,
                20
            )
        )
    }

    @Test
    fun periodOneReturnsLatestValue() {
        check(
            5.0,
            IndicatorMath.sma(values, 1)
        )

        check(
            5.0,
            IndicatorMath.ema(values, 1)
        )

        check(
            5.0,
            IndicatorMath.rma(values, 1)
        )

        check(
            0.0,
            IndicatorMath.standardDeviation(
                values,
                1
            )
        )
    }

    private fun assertUnavailable(
        input: List<Double>,
        period: Int
    ) {
        assertNull(
            IndicatorMath.sma(input, period)
        )

        assertNull(
            IndicatorMath.ema(input, period)
        )

        assertNull(
            IndicatorMath.rma(input, period)
        )

        assertNull(
            IndicatorMath.standardDeviation(
                input,
                period
            )
        )
    }

    @Test
    fun insufficientHistoryReturnsNull() {
        assertUnavailable(values, 6)
    }

    @Test
    fun emptyHistoryReturnsNull() {
        assertUnavailable(emptyList(), 1)
    }

    @Test
    fun zeroPeriodReturnsNull() {
        assertUnavailable(values, 0)
    }

    @Test
    fun negativePeriodReturnsNull() {
        assertUnavailable(values, -1)
    }

    @Test
    fun nanInputReturnsNull() {
        assertUnavailable(
            listOf(1.0, Double.NaN, 3.0),
            2
        )
    }

    @Test
    fun positiveInfinityReturnsNull() {
        assertUnavailable(
            listOf(
                1.0,
                Double.POSITIVE_INFINITY
            ),
            2
        )
    }

    @Test
    fun negativeInfinityReturnsNull() {
        assertUnavailable(
            listOf(
                Double.NEGATIVE_INFINITY,
                2.0
            ),
            2
        )
    }

    @Test
    fun invalidOlderHistoryIsRejected() {
        assertUnavailable(
            listOf(
                Double.NaN,
                1.0,
                2.0,
                3.0
            ),
            2
        )
    }

    @Test
    fun varianceOverflowReturnsNull() {
        assertNull(
            IndicatorMath.standardDeviation(
                listOf(1e200, -1e200),
                2
            )
        )
    }
}
