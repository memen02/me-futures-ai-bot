package com.mefuturesaibot.app.exchange

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class SmaSignalTest {
    private fun history(minutes: IntRange = 0..119, price: (Int) -> Int): CandleHistory {
        val candles = minutes.map {
            val close = BigDecimal(price(it))
            Candle(it * 60_000L, close, close, close, close, BigDecimal.ONE)
        }
        return CandleHistory(candles, "Riwayat tersinkron", syncOk = true)
    }

    @Test fun bothIntervalsMustAgree() {
        val now = 120 * 60_000L
        assertEquals(SignalSide.BUY, history { it + 1 }.signalAt(now).side)
        assertEquals(SignalSide.SELL, history { 200 - it }.signalAt(now).side)
        assertEquals(SignalSide.WAIT, history { 10 }.signalAt(now).side)
        val mixed = history { if (it < 100) 20 else if (it == 119) 4 else 1 }
        assertEquals(SignalSide.WAIT, mixed.signalAt(now).side)
    }

    @Test fun detailsUseTheSameClosedCandlesAsTheSignal() {
        val signal = history(1..120) { it + 1 }.signalAt(121 * 60_000L)
        val (one, five) = signal.intervals
        assertEquals(1, one.minutes)
        assertEquals(0, one.close.compareTo(BigDecimal("121")))
        assertEquals(0, one.sma.compareTo(BigDecimal("111.5")))
        assertEquals(121 * 60_000L - 1, one.closedAtMs)
        assertEquals(5, five.minutes)
        assertEquals(0, five.close.compareTo(BigDecimal("120")))
        assertEquals(0, five.sma.compareTo(BigDecimal("72.5")))
        assertEquals(120 * 60_000L - 1, five.closedAtMs)
        assertEquals(2, history { 10 }.signalAt(120 * 60_000L).intervals.size)
        assertEquals(0, history { 10 }.signalAt(121 * 60_000L).intervals.size)
    }

    @Test fun formingFiveMinuteBarDoesNotCount() {
        // The 1m close jumped above its SMA, but the latest completed 5m bar
        // still closed below its SMA. The new bucket has only one minute.
        val partial = history(1..120) { if (it == 120) 500 else 200 - it }
        assertEquals(SignalSide.WAIT, partial.signalAt(121 * 60_000L).side)
        val incompleteFirst = history(1..99) { it }
        assertEquals(SignalSide.WAIT, incompleteFirst.signalAt(100 * 60_000L).side)
    }

    @Test fun staleSnapshotExpiresAtExchangeMinuteBoundaryAndRecovers() {
        val old = history { it + 1 }
        assertEquals(SignalSide.BUY, old.signalAt(121 * 60_000L - 1).side)
        assertEquals("Riwayat tertinggal", old.statusAt(121 * 60_000L))
        assertEquals(SignalSide.WAIT, old.signalAt(121 * 60_000L).side)
        assertEquals(SignalSide.BUY, history(1..120) { it + 1 }.signalAt(121 * 60_000L).side)
    }

    @Test fun missingClockFailedRestOrEmptyHistoryReturnsWait() {
        val good = history { it + 1 }
        assertEquals(SignalSide.WAIT, good.signalAt(null).side)
        val failed = good.copy(status = "Riwayat gagal dimuat (HTTP 451)", syncOk = false)
        assertEquals(SmaSignal.waitFor(failed.status), failed.signalAt(120 * 60_000L))
        assertEquals(SignalSide.WAIT, CandleHistory().signalAt(120 * 60_000L).side)
    }
}
