package com.mefuturesaibot.app.scanner

import com.mefuturesaibot.app.exchange.SignalSide
import com.mefuturesaibot.app.exchange.SmaSignal

/** Directional decision before account, quote, funding and order-protection vetoes. */
internal object BaselineDecision {
    const val VERSION = "logit-sma20-v1"
    fun evaluate(row: MarketRow?, at: Long?, sma: SmaSignal): SmaSignal {
        fun wait(reason: String) = SmaSignal(SignalSide.WAIT, reason, sma.intervals)
        if (row == null || at == null || row.ticker?.fresh(at) != true) return wait("Ticker atau waktu bursa belum mutakhir")
        if (row.contract.quote != "USDT" || row.ticker.volume < 1_000_000) return wait("Di luar cakupan USDT atau volume di bawah batas")
        val score = row.score ?: return wait("Menunggu pemanasan model atau skor valid")
        if (!score.isFinite() || score !in 0.0..1.0) return wait("Skor model tidak valid")
        if (sma.side == SignalSide.WAIT) return sma
        val side = when {
            score > 0.57 -> SignalSide.BUY
            score < 0.43 -> SignalSide.SELL
            else -> SignalSide.WAIT
        }
        return if (side == sma.side) sma.copy(reason = "Model searah · ${sma.reason}")
            else wait("Skor model dan konfirmasi SMA belum searah")
    }
}
