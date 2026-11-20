package com.mefuturesaibot.app

import com.mefuturesaibot.app.exchange.*
import com.mefuturesaibot.app.exchange.ReconnectingWebSocket.ConnectionStatus
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class StartupChecksTest {
    private val clock = ExchangeClock().apply { observe(120 * 60_000L, 0L) }
    private val quote = BookQuote("99", "100", 120 * 60_000L, 0)
    private val history = CandleHistory((0..119).map {
        Candle(it * 60_000L, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)
    }, "Riwayat tersinkron", true)

    @Test fun handshakeAloneOnlyChecksFirstRow() {
        val rows = startupRows(ConnectionStatus.CONNECTED, null, CandleHistory(), clock, 0, false)
        assertEquals(listOf(CheckState.PASSED, CheckState.PENDING, CheckState.PENDING), rows.map { it.state })
    }

    @Test fun requiresFreshQuoteAndCurrentHistoryForAllGreen() {
        assertTrue(startupRows(ConnectionStatus.CONNECTED, quote, history, clock, 100, false)
            .all { it.state == CheckState.PASSED })
        val delayed = startupRows(ConnectionStatus.CONNECTED, quote, history, clock, 60_001, true)
        assertEquals(listOf(CheckState.PASSED, CheckState.FAILED, CheckState.FAILED), delayed.map { it.state })
    }

    @Test fun restFailureAndDisconnectedSocketKeepTheirOwnStatus() {
        val failure = CandleHistory(status = "Riwayat gagal dimuat (HTTP 451)")
        val rows = startupRows(ConnectionStatus.CONNECTED, quote, failure, clock, 100, false)
        assertEquals(listOf(CheckState.PASSED, CheckState.PASSED, CheckState.FAILED), rows.map { it.state })
        assertTrue(rows.last().detail.contains("451"))
        val offline = startupRows(ConnectionStatus.RECONNECTING, quote, history, clock, 100, true)
        assertEquals(listOf(CheckState.FAILED, CheckState.FAILED, CheckState.PASSED), offline.map { it.state })
    }
}
