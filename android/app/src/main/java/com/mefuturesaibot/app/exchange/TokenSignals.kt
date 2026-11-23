package com.mefuturesaibot.app.exchange

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** Serial requests limited to the symbols currently visible. Cache expires at the next exchange minute. */
internal class TokenSignals(private val changed: (String, SmaSignal) -> Unit) {
    private data class Entry(val signal: SmaSignal, val expires: Long)
    private val reader = PublicReader()
    private val main = Handler(Looper.getMainLooper())
    private val cache = android.util.LruCache<String, Entry>(40)
    private var symbols = emptyList<String>()
    private var busy = false

    fun signal(symbol: String): SmaSignal = cache.get(symbol)?.takeIf { it.expires > SystemClock.elapsedRealtime() }?.signal
        ?: SmaSignal.waitFor("Menunggu candle tertutup 1m dan 5m")

    fun watch(visible: List<String>) {
        symbols = visible.distinct()
        if (!busy) next()
    }

    private fun next() {
        val now = SystemClock.elapsedRealtime()
        val symbol = symbols.firstOrNull { (cache.get(it)?.expires ?: -1) <= now } ?: return
        busy = true
        val started = SystemClock.elapsedRealtime()
        reader.read("klines?symbol=${PublicReader.encode(symbol)}&interval=1m&limit=121") { body, at, error ->
            val result = if (body == null) SmaSignal.waitFor(error) else runCatching {
                CandleHistory(CandleHistory.parseClosedCandles(body), "Siap", true).signalAt(at)
            }.getOrElse { SmaSignal.waitFor("Riwayat 1m/5m belum lengkap") }
            val life = if (body != null && at != null) 60_000 - at % 60_000 else 30_000
            cache.put(symbol, Entry(result, started + life))
            if (symbol in symbols) changed(symbol, signal(symbol))
            main.postDelayed({ busy = false; next() }, 1000)
        }
    }

    fun stop() {
        reader.cancel()
        main.removeCallbacksAndMessages(null)
        symbols = emptyList()
        busy = false
    }
}
