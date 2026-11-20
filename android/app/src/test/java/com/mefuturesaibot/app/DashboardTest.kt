package com.mefuturesaibot.app

import android.view.View
import android.widget.Switch
import android.widget.TextView
import com.mefuturesaibot.app.exchange.BookQuote
import com.mefuturesaibot.app.exchange.Candle
import com.mefuturesaibot.app.exchange.CandleHistory
import com.mefuturesaibot.app.exchange.ExchangeClock
import com.mefuturesaibot.app.exchange.ReconnectingWebSocket.ConnectionStatus
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w320dp-h640dp")
class DashboardTest {
    @Test fun dashboardRendersOnlyAvailableDataAndClearsItOnStop() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            fun label(id: Int) = activity.findViewById<TextView>(id).text.toString()
            fun visibility(id: Int) = activity.findViewById<View>(id).visibility
            assertEquals("WAIT", label(R.id.dash_signal))
            assertEquals("—", label(R.id.dash_bid))
            assertEquals(View.GONE, visibility(R.id.dash_chart))
            val candles = (0 until 120).map { i ->
                val price = BigDecimal.valueOf(100L + i)
                Candle(i * 60_000L, price, price, price, price, BigDecimal.ONE)
            }
            val now = 120 * 60_000L
            val history = CandleHistory(candles, "Riwayat tersinkron", true)
            val clock = ExchangeClock().apply { observe(now, 0) }
            val quote = BookQuote("219.10", "219.20", now, 0)
            activity.renderMarket(ConnectionStatus.CONNECTED, quote, history, clock, 1)
            assertEquals("BUY", label(R.id.dash_signal))
            assertEquals("219.10", label(R.id.dash_bid))
            assertEquals("219.20", label(R.id.dash_ask))
            assertEquals(View.VISIBLE, visibility(R.id.dash_chart))
            assertTrue(label(R.id.dash_chart_key).contains("219"))
            assertTrue(label(R.id.dash_sma_details).contains("5 menit"))
            activity.renderMarket(ConnectionStatus.DISCONNECTED, quote.copy(disconnected = true), history, clock, 10_000)
            assertEquals("WAIT", label(R.id.dash_signal))
            assertTrue(label(R.id.dash_quote_time).contains("kedaluwarsa"))
            activity.findViewById<Switch>(R.id.dash_auto_start).isChecked = true
            assertTrue(BotPreferences.isAutoStartEnabled(activity))
            activity.findViewById<View>(R.id.dash_stop).performClick()
            assertEquals(View.GONE, visibility(R.id.dash_chart))
            assertEquals(View.GONE, visibility(R.id.dash_sma_panel))
            assertEquals("—", label(R.id.dash_ask))
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
