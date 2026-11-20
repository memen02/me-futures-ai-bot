package com.mefuturesaibot.app.exchange

import java.math.BigDecimal

enum class SignalSide { WAIT, BUY, SELL }

data class SmaInterval(
    val minutes: Int,
    val closedAtMs: Long,
    val close: BigDecimal,
    val sma: BigDecimal,
) {
    val direction: Int get() = close.compareTo(sma)
}

data class SmaSignal(val side: SignalSide, val reason: String, val intervals: List<SmaInterval> = emptyList()) {
    companion object {
        private const val PERIOD = 20
        fun waitFor(reason: String) = SmaSignal(SignalSide.WAIT, reason)

        fun fromClosedCandles(candles: List<Candle>): SmaSignal {
            // parseClosedCandles menjamin urutan menit berkesinambungan. Candle 5m
            // berakhir pada menit 04, 09, dst. Lewati kelompok awal yang tak lengkap.
            val first = candles.firstOrNull()?.openTime ?: return waitFor("Menunggu candle tertutup")
            val fiveMinuteMs = 5 * CandleHistory.INTERVAL_MS
            val fiveMinuteCloses = candles.filter {
                val end = it.openTime + CandleHistory.INTERVAL_MS
                end % fiveMinuteMs == 0L && end - fiveMinuteMs >= first
            }
            if (fiveMinuteCloses.size < PERIOD) return waitFor("Menunggu 20 candle lengkap pada kedua interval")
            val one = interval(candles, 1)
            val five = interval(fiveMinuteCloses, 5)
            val details = listOf(one, five)
            return when {
                one.direction > 0 && five.direction > 0 -> SmaSignal(SignalSide.BUY, "Penutupan 1m dan 5m di atas SMA 20", details)
                one.direction < 0 && five.direction < 0 -> SmaSignal(SignalSide.SELL, "Penutupan 1m dan 5m di bawah SMA 20", details)
                else -> SmaSignal(SignalSide.WAIT, "Arah SMA 20 pada 1m dan 5m belum sejalan", details)
            }
        }

        private fun interval(candles: List<Candle>, minutes: Int): SmaInterval {
            val closes = candles.takeLast(PERIOD).map { it.close }
            val sma = closes.reduce(BigDecimal::add).divide(BigDecimal.valueOf(PERIOD.toLong()))
            return SmaInterval(minutes, candles.last().openTime + CandleHistory.INTERVAL_MS - 1, closes.last(), sma)
        }
    }
}
