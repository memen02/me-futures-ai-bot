package com.mefuturesaibot.app.exchange

import org.json.JSONArray
import java.math.BigDecimal

data class Candle(
    val openTime: Long,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: BigDecimal,
)

data class CandleHistory(
    val candles: List<Candle> = emptyList(),
    val status: String = "Menunggu riwayat",
    val syncOk: Boolean = false,
) {
    private val trend by lazy { SmaSignal.fromClosedCandles(candles) }

    private fun waitReason(exchangeTimeMs: Long?): String? {
        if (!syncOk) return status
        if (exchangeTimeMs == null) return "Menunggu waktu bursa"
        val expectedOpen = exchangeTimeMs - exchangeTimeMs % INTERVAL_MS - INTERVAL_MS
        if (candles.lastOrNull()?.openTime != expectedOpen) return "Riwayat tertinggal"
        return null
    }

    fun statusAt(exchangeTimeMs: Long?) = waitReason(exchangeTimeMs) ?: status

    fun signalAt(exchangeTimeMs: Long?): SmaSignal {
        val reason = waitReason(exchangeTimeMs)
        return if (reason != null) SmaSignal.waitFor(reason) else trend
    }

    companion object {
        const val SIZE = 120
        const val INTERVAL_MS = 60_000L

        // Baris terbaru berpotensi masih terbuka. Adanya menit berikutnya
        // membuktikan candle sebelumnya sudah ditutup menurut waktu bursa.
        fun parseClosedCandles(body: String): List<Candle> {
            val rows = JSONArray(body)
            val byMinute = sortedMapOf<Long, Candle>()
            for (i in 0 until rows.length()) {
                val row = rows.getJSONArray(i)
                val time = row.getLong(0)
                require(time >= 0 && time % INTERVAL_MS == 0L)
                require(row.getLong(6) == time + INTERVAL_MS - 1)
                val candle = Candle(time, row.getString(1).toBigDecimal(), row.getString(2).toBigDecimal(),
                    row.getString(3).toBigDecimal(), row.getString(4).toBigDecimal(), row.getString(5).toBigDecimal())
                require(candle.low.signum() > 0 && candle.volume.signum() >= 0)
                require(candle.open in candle.low..candle.high && candle.close in candle.low..candle.high)
                byMinute[time] = candle
            }
            val window = byMinute.values.toList().takeLast(SIZE + 1)
            require(window.size == SIZE + 1)
            require(window.zipWithNext().all { (a, b) -> b.openTime - a.openTime == INTERVAL_MS })
            return window.dropLast(1)
        }
    }
}
