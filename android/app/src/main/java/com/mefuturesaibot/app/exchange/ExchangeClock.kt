package com.mefuturesaibot.app.exchange

/** Anchor waktu bursa ke elapsedRealtime (termasuk waktu HP tidur). Diakses di main thread. */
class ExchangeClock {
    private var offsetMs: Long? = null

    fun observe(exchangeMs: Long, receivedElapsedMs: Long) {
        val offset = exchangeMs - receivedElapsedMs
        // Paket yang terlambat tidak boleh memundurkan estimasi waktu bursa.
        offsetMs = maxOf(offsetMs ?: offset, offset)
    }

    fun at(elapsedMs: Long): Long? = offsetMs?.plus(elapsedMs)
}
