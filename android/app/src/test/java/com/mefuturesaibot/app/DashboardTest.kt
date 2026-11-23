package com.mefuturesaibot.app

import com.mefuturesaibot.app.exchange.PublicChartFeed
import com.mefuturesaibot.app.exchange.CandleScheduler
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val callbacks = CandleScheduler()
        val fixture = """[[0,"200","205","195","203","7",59999],[60000,"203","220","200","219","2",119999]]"""
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(fixture.toResponseBody()).build()
        }.build()
        val screen = controller.get()
        screen.chartFeed = PublicChartFeed(client, callbacks, { 0L }, screen::renderChart)
        controller.start().resume().visible()
        try {
            val activity = controller.get()
            fun label(id: Int) = activity.findViewById<TextView>(id).text.toString()
            fun visibility(id: Int) = activity.findViewById<View>(id).visibility
            assertEquals("WAIT", label(R.id.dash_signal))
            assertEquals("—", label(R.id.dash_bid))
            assertEquals(View.GONE, visibility(R.id.dash_chart))
            callbacks.nextCallback().invoke()
            assertEquals(View.VISIBLE, visibility(R.id.dash_chart))
            assertEquals("219", label(R.id.dash_last_price))
            assertFalse(BotPreferences.shouldRun(activity))
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
            assertTrue(label(R.id.dash_chart_key).contains("Maks 220"))
            assertTrue(label(R.id.desk_spread).contains("0.10 USDT"))
            assertTrue(label(R.id.dash_sma_details).contains("5 menit"))
            activity.renderMarket(ConnectionStatus.DISCONNECTED, quote.copy(disconnected = true), history, clock, 10_000)
            assertEquals("WAIT", label(R.id.dash_signal))
            assertTrue(label(R.id.dash_quote_time).contains("kedaluwarsa"))
            activity.findViewById<View>(R.id.nav_account).performClick()
            activity.findViewById<Switch>(R.id.account_autostart).isChecked = true
            assertTrue(BotPreferences.isAutoStartEnabled(activity))
            activity.findViewById<View>(R.id.nav_market).performClick()
            activity.findViewById<View>(R.id.scan_stop).performClick()
            activity.findViewById<View>(R.id.nav_home).performClick()
            assertEquals(View.VISIBLE, visibility(R.id.dash_chart))
            assertEquals("219", label(R.id.dash_last_price))
            assertFalse(BotPreferences.shouldRun(activity))
            assertEquals(View.GONE, visibility(R.id.dash_sma_panel))
            assertEquals("—", label(R.id.dash_ask))
            activity.findViewById<View>(R.id.dash_interval).performClick()
            val picker = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
            assertEquals(16, picker.listView.count)
            val monthly = picker.listView.count - 1
            picker.listView.performItemClick(picker.listView.adapter.getView(monthly, null, picker.listView), monthly, monthly.toLong())
            assertEquals("1M", screen.chartFeed.state.interval)
            while (!screen.chartFeed.state.fresh) callbacks.nextCallback().invoke()
            assertTrue(label(R.id.dash_interval).contains("1M"))
            assertEquals(View.VISIBLE, visibility(R.id.dash_chart))
        } finally {
            controller.pause().stop().destroy()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
