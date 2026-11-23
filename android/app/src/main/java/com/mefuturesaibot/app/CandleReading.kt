package com.mefuturesaibot.app

import com.mefuturesaibot.app.exchange.Candle
import java.math.BigDecimal

/** At index i use the twenty preceding bars for levels, and only the selected close for confirmation. */
internal data class CandleReading(val support: BigDecimal, val resistance: BigDecimal,
    val fib: BigDecimal?, val label: String, val invalidation: String) {
    companion object {
        fun at(bars: List<Candle>, i: Int): CandleReading? {
            if (i < 20 || i >= bars.lastIndex) return null
            val window = bars.subList(i - 20, i)
            val low = window.minOf { it.low }
            val high = window.maxOf { it.high }
            val lowAt = window.indexOfLast { it.low == low }
            val highAt = window.indexOfLast { it.high == high }
            val up = highAt > lowAt
            val retrace = (high - low) * BigDecimal("0.618")
            val fib = when { lowAt == highAt -> null; up -> high - retrace; else -> low + retrace }
            val c = bars[i]
            val (label, cancel) = when {
                c.close > high -> "Breakout naik" to "Kelanjutan naik batal bila penutupan kembali ≤ ${high.plain()}."
                c.close < low -> "Breakout turun" to "Kelanjutan turun batal bila penutupan kembali ≥ ${low.plain()}."
                c.low < low && c.high > high -> "Penolakan dua sisi" to "Tunggu penutupan di luar rentang."
                c.low < low -> "Penurunan ditolak" to "Skenario pantulan batal bila penutupan < ${low.plain()}."
                c.high > high -> "Kenaikan ditolak" to "Skenario penolakan batal bila penutupan > ${high.plain()}."
                else -> "Di dalam rentang" to "Penutupan di luar support atau resistance membatalkan rentang."
            }
            return CandleReading(low, high, fib, label, cancel)
        }
    }
}
internal fun BigDecimal.plain(): String = stripTrailingZeros().toPlainString()
