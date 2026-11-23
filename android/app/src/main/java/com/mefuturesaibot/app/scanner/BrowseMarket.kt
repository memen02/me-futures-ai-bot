package com.mefuturesaibot.app.scanner

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.mefuturesaibot.app.exchange.PublicReader

/** Read-only market browsing while the foreground scanner is stopped. */
internal class BrowseMarket(private val publish: (Scan) -> Unit) {
    private val reader = PublicReader()
    private val main = Handler(Looper.getMainLooper())
    private var contracts = emptyList<Contract>()
    private var refreshAt = 0L
    private var last = Scan(status = "Memuat pasar publik")
    fun start() {
        if (contracts.isEmpty() || SystemClock.elapsedRealtime() >= refreshAt) {
            reader.read("exchangeInfo") { body, _, error ->
                val registry = body?.let { runCatching { MarketData.contracts(it) }.getOrNull() }
                if (registry == null) failed(error) else {
                    contracts = registry
                    refreshAt = SystemClock.elapsedRealtime() + 600_000
                    tickers()
                }
            }
        } else tickers()
    }
    private fun tickers() {
        reader.read("ticker/24hr") { body, at, error ->
            val tickers = body?.let { runCatching { MarketData.tickers(it) }.getOrNull() }
            if (tickers == null || at == null) failed(error) else {
                last = Scan(contracts.map { MarketRow(it, tickers[it.symbol], null) }, at,
                    SystemClock.elapsedRealtime(), "Pasar publik", true)
                publish(last)
                main.postDelayed(::start, 60_000)
            }
        }
    }
    private fun failed(error: String) {
        last = last.copy(ok = false, status = error.ifBlank { "Data pasar belum valid" })
        publish(last)
        main.postDelayed(::start, 60_000)
    }
    fun close() { reader.cancel(); main.removeCallbacksAndMessages(null) }
}
