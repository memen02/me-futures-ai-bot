package com.mefuturesaibot.app.exchange

import java.math.BigDecimal

enum class SignalSide { WAIT, BUY, SELL }

data class SmaSignal(val side: SignalSide, val reason: String) {
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
            val one = direction(candles)
            val five = direction(fiveMinuteCloses)
            return when {
                one > 0 && five > 0 -> SmaSignal(SignalSide.BUY, "Penutupan 1m dan 5m di atas SMA 20")
                one < 0 && five < 0 -> SmaSignal(SignalSide.SELL, "Penutupan 1m dan 5m di bawah SMA 20")
                else -> waitFor("Arah SMA 20 pada 1m dan 5m belum sejalan")
            }
        }

        private fun direction(candles: List<Candle>): Int {
            val closes = candles.takeLast(PERIOD).map { it.close }
            val sma = closes.reduce(BigDecimal::add).divide(BigDecimal.valueOf(PERIOD.toLong()))
            return closes.last().compareTo(sma)
        }
    }
}
