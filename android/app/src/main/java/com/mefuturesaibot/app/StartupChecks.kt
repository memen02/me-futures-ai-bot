package com.mefuturesaibot.app

import com.mefuturesaibot.app.exchange.BookQuote
import com.mefuturesaibot.app.exchange.CandleHistory
import com.mefuturesaibot.app.exchange.ExchangeClock
import com.mefuturesaibot.app.exchange.ReconnectingWebSocket.ConnectionStatus

internal enum class CheckState { PENDING, PASSED, FAILED }
internal data class StartupRow(val state: CheckState, val detail: String)

internal fun startupRows(
    connection: ConnectionStatus,
    quote: BookQuote?,
    history: CandleHistory,
    clock: ExchangeClock,
    elapsed: Long,
    timedOut: Boolean,
): List<StartupRow> {
    fun pending(detail: String) = if (timedOut) StartupRow(CheckState.FAILED, "Waktu habis · $detail")
        else StartupRow(CheckState.PENDING, detail)
    fun passed(detail: String) = StartupRow(CheckState.PASSED, detail)
    val market = when (connection) {
        ConnectionStatus.CONNECTED -> passed("WebSocket tersambung")
        ConnectionStatus.CONNECTING -> pending("Membuka WebSocket")
        else -> pending("Koneksi terputus · mencoba kembali")
    }
    val price = if (connection == ConnectionStatus.CONNECTED && quote != null && !quote.isStale(elapsed, clock))
        passed("Bid dan ask baru diterima") else pending("Menunggu pembaruan harga")
    val status = history.statusAt(clock.at(elapsed))
    val candles = when {
        history.syncOk && history.candles.size == CandleHistory.SIZE && status == history.status ->
            passed("${history.candles.size} candle tertutup · 1 menit")
        !history.syncOk && history.status != CandleHistory().status ->
            StartupRow(CheckState.FAILED, history.status)
        else -> pending(status)
    }
    return listOf(market, price, candles)
}


/** Dashboard readiness is independent of candle analysis readiness. */
internal fun startupReady(rows: List<StartupRow>): Boolean =
    rows.size == 3 &&
        rows[0].state == CheckState.PASSED &&
        rows[1].state == CheckState.PASSED
