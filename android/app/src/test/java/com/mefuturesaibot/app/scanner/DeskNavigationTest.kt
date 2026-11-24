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
            val labels = listOf("Beranda", "Pasar", "Bot", "Trading", "Akun")
            DeskTab.values().forEachIndexed { index, target ->
                val button = activity.findViewById<TextView>(target.buttonId)
                assertEquals(labels[index], button.text.toString())
                button.performClick()
                DeskTab.values().forEach {
                    assertEquals(it == target, activity.findViewById<View>(it.pageId).isShown)
                    assertEquals(it == target, activity.findViewById<View>(it.buttonId).isSelected)
                }
            }
            activity.findViewById<View>(R.id.nav_account).performClick()
            activity.findViewById<View>(R.id.nav_trading).performClick()
            controller.recreate()
            assertTrue(controller.get().findViewById<View>(R.id.page_trading).isShown)
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
            assertEquals(2, list.count)
            activity.findViewById<EditText>(R.id.scan_query).setText("eth")
            assertEquals(1, list.count)
            assertEquals("ETHUSDT", (list.getItemAtPosition(0) as MarketRow).contract.symbol)
            desk.releaseDisplayData()
            assertEquals(0, list.count)
            desk.update(scan, true)
            assertEquals("ETHUSDT", (list.getItemAtPosition(0) as MarketRow).contract.symbol)
            list.performItemClick(list.adapter.getView(0, null, list), 0, 0)
            val opened = org.robolectric.Shadows.shadowOf(activity).nextStartedActivity
            assertEquals("ETHUSDT", opened.getStringExtra("symbol"))
            assertEquals("ETH", opened.getStringExtra("base"))
            desk.close()
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun homeShowsMarketRowsWithoutBotAndRemovesExpiredPrices() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        try {
            val activity = controller.get()
            val desk = TradingDesk(activity, DeskTab.HOME) {}
            val ticker = Ticker(10.0, -2.0, 11.0, 9.0, 2e6, 10.0, 200_000)
            val rows = listOf(MarketRow(Contract("ETHUSDT", "USDT"), ticker, null))
            val scan = Scan(rows, ticker.time, SystemClock.elapsedRealtime(), "Pasar publik", true)
            desk.update(scan, false)
            val list = activity.findViewById<android.widget.LinearLayout>(R.id.desk_watch_rows)
            assertEquals(1, list.childCount)
            assertEquals("WAIT", list.getChildAt(0).findViewById<TextView>(R.id.row_model).text)
            assertTrue(activity.findViewById<TextView>(R.id.desk_coverage).text.contains("1 turun"))
            desk.update(scan.copy(time = ticker.time + 120_001), false)
            assertEquals(0, list.childCount)
            desk.close()
        } finally { controller.destroy() }
    }

    @Test fun tabsLeadToMarketPaperPositionsJournalAndAccount() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            val home = activity.findViewById<View>(R.id.page_home)
            assertNull(home.findViewById<View>(R.id.scan_start))
            assertNull(home.findViewById<View>(R.id.scan_stop))
            activity.findViewById<View>(R.id.nav_market).performClick()
            assertFalse(activity.findViewById<View>(R.id.scan_start).isShown)
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
            assertTrue(activity.findViewById<View>(R.id.paper_journal).isShown)
            activity.findViewById<View>(R.id.nav_bot).performClick()
            assertTrue(activity.findViewById<View>(R.id.paper_enabled).isShown)
            assertTrue(activity.findViewById<View>(R.id.account_autostart).isShown)
            assertTrue(activity.findViewById<View>(R.id.scan_start).isShown)
            activity.findViewById<View>(R.id.nav_account).performClick()
            assertTrue(activity.findViewById<View>(R.id.demo_keys).isShown)
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
