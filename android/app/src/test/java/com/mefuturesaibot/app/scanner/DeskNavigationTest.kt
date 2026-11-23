package com.mefuturesaibot.app.scanner

import android.os.SystemClock
import android.view.View
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import com.mefuturesaibot.app.MainActivity
import com.mefuturesaibot.app.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w320dp-h640dp")
class DeskNavigationTest {
    @Test fun fiveTabsAreExclusiveAndViewingThemDoesNotStartTrading() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            val labels = listOf("Beranda", "Pasar", "Trading", "Riwayat", "Akun")
            DeskTab.values().forEachIndexed { index, target ->
                val button = activity.findViewById<TextView>(target.buttonId)
                assertEquals(labels[index], button.text.toString())
                button.performClick()
                DeskTab.values().forEach {
                    assertEquals(it == target, activity.findViewById<View>(it.pageId).isShown)
                    assertEquals(it == target, activity.findViewById<View>(it.buttonId).isSelected)
                }
            }
            activity.findViewById<View>(R.id.nav_home).performClick()
            activity.findViewById<View>(R.id.desk_history).performClick()
            controller.recreate()
            assertTrue(controller.get().findViewById<View>(R.id.page_history).isShown)
            assertFalse(com.mefuturesaibot.app.BotPreferences.shouldRun(activity))
            assertFalse(DeskStore(activity).simulation)
            com.mefuturesaibot.app.demo.FuturesVenue.values().forEach {
                assertFalse(it.prefs(activity).getBoolean("enabled",false))
                assertFalse(it.prefs(activity).contains("intent"))
            }
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun savedFourTabLayoutKeepsTheAccountDestination() {
        val state = android.os.Bundle()
        state.putInt("deskTab",3)
        assertEquals(DeskTab.ACCOUNT, DeskTab.restore(state))
        state.putInt("deskTab",2)
        assertEquals(DeskTab.TRADING, DeskTab.restore(state))
        state.putString(DeskTab.STATE_KEY, "UNKNOWN")
        assertEquals(DeskTab.HOME, DeskTab.restore(state))
    }

    @Test fun scannerSearchAndFavoritesOperateOnRealAdapterRows() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            DeskStore(activity).favorites = setOf("ETHUSDT")
            val desk = TradingDesk(activity, DeskTab.MARKET) {}
            val ticker = Ticker(150.0, -2.0, 180.0, 140.0, 1_500_000.0, 155.0, 1000)
            val rows = listOf(MarketRow(Contract("ETHUSDT", "USDT"), ticker, 0.30),
                MarketRow(Contract("BTCUSDT", "USDT"), null, null))
            val scan = Scan(rows, 1000, SystemClock.elapsedRealtime(), "Aktif", true)
            desk.update(scan, true)
            val list = activity.findViewById<ListView>(R.id.scan_list)
            assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.scan_stop).visibility)
            assertEquals(View.GONE, activity.findViewById<View>(R.id.scan_start).visibility)
            assertEquals("—", activity.findViewById<TextView>(R.id.dash_last_price).text.toString())
            assertEquals(2, list.count)
            activity.findViewById<EditText>(R.id.scan_query).setText("eth")
            assertEquals(1, list.count)
            assertEquals("ETHUSDT", (list.getItemAtPosition(0) as MarketRow).contract.symbol)
            desk.releaseDisplayData()
            assertEquals(0, list.count)
            desk.update(scan, true)
            assertEquals("ETHUSDT", (list.getItemAtPosition(0) as MarketRow).contract.symbol)
            list.performItemClick(list.adapter.getView(0, null, list), 0, 0)
            val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
            assertTrue(dialog.isShowing)
            assertTrue(dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).text.toString().contains("Hapus"))
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun btcSummaryUsesFreshBtcTickerAndClearsOnExpiryOrStop() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            val desk = TradingDesk(activity, DeskTab.HOME) {}
            val btc = Ticker(62105.4, -2.0, 64000.0, 61000.0, 1_500_000.0, 62500.0, 200_000)
            val rows = listOf(MarketRow(Contract("ETHUSDT", "USDT"), btc.copy(price = 1300.0), null),
                MarketRow(Contract("BTCUSDT", "USDT"), btc, null))
            val scan = Scan(rows, btc.time, SystemClock.elapsedRealtime(), "Aktif", true)
            fun label(id: Int) = activity.findViewById<TextView>(id).text.toString()
            desk.update(scan, true)
            assertEquals("62,105.40", label(R.id.dash_last_price))
            assertEquals("1,000.00", label(R.id.desk_balance))
            assertTrue(label(R.id.desk_coverage).contains("2 turun"))
            assertEquals(2, activity.findViewById<android.widget.LinearLayout>(R.id.desk_watch_rows).childCount)
            assertEquals("64,000.00", label(R.id.dash_high))
            assertEquals("61,000.00", label(R.id.dash_low))
            assertTrue(label(R.id.dash_change).contains("-2.00%"))
            desk.update(scan.copy(time = btc.time + 120_001), true)
            assertEquals("—", label(R.id.dash_last_price))
            desk.update(scan, false)
            assertEquals("—", label(R.id.dash_high))
            assertTrue(label(R.id.dash_ticker_time).contains("nonaktif"))
            assertEquals(0, activity.findViewById<android.widget.LinearLayout>(R.id.desk_watch_rows).childCount)
            desk.close()
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun tabsLeadToMarketPaperPositionsJournalAndAccount() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            val home = activity.findViewById<View>(R.id.page_home)
            assertNull(home.findViewById<View>(R.id.scan_start))
            assertNull(home.findViewById<View>(R.id.scan_stop))
            activity.findViewById<View>(R.id.nav_market).performClick()
            assertTrue(activity.findViewById<View>(R.id.scan_start).isShown)
            assertFalse(activity.findViewById<View>(R.id.scan_stop).isShown)
            assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.page_scanner).visibility)
            assertEquals(View.GONE, activity.findViewById<View>(R.id.page_home).visibility)
            assertEquals(0, activity.findViewById<ListView>(R.id.scan_list).count)
            activity.findViewById<EditText>(R.id.scan_query).setText("ETH")
            activity.findViewById<View>(R.id.nav_trading).performClick()
            assertTrue(activity.findViewById<TextView>(R.id.paper_model).text.contains("0/30"))
            assertTrue(activity.findViewById<TextView>(R.id.account_wallet).text.contains("USDT"))
            assertTrue(activity.findViewById<View>(R.id.paper_open).isShown)
            assertFalse(activity.findViewById<View>(R.id.paper_enabled).isShown)
            activity.findViewById<View>(R.id.nav_history).performClick()
            assertTrue(activity.findViewById<View>(R.id.paper_journal).isShown)
            assertFalse(activity.findViewById<View>(R.id.paper_open).isShown)
            activity.findViewById<View>(R.id.nav_account).performClick()
            assertTrue(activity.findViewById<View>(R.id.paper_enabled).isShown)
            val key = activity.findViewById<EditText>(R.id.account_key)
            val secret = activity.findViewById<EditText>(R.id.account_secret)
            assertFalse(key.isSaveEnabled)
            assertFalse(secret.isSaveEnabled)
            assertTrue(secret.transformationMethod is android.text.method.PasswordTransformationMethod)
            key.setText("example-key")
            activity.findViewById<View>(R.id.account_save).performClick()
            assertNotNull(secret.error)
            assertTrue(activity.findViewById<TextView>(R.id.account_connection).text.contains("Belum terhubung"))
            activity.findViewById<View>(R.id.nav_home).performClick()
            assertTrue(key.text.isEmpty())
            assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.page_home).visibility)
        } finally { controller.pause().stop().destroy() }
    }
}
