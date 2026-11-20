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
    @Test fun scannerSearchAndFavoritesOperateOnRealAdapterRows() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            DeskStore(activity).favorites = setOf("ETHUSDT")
            val desk = TradingDesk(activity, 1) {}
            val ticker = Ticker(150.0, -2.0, 180.0, 140.0, 1_500_000.0, 155.0, 1000)
            val rows = listOf(MarketRow(Contract("ETHUSDT", "USDT"), ticker, 0.30),
                MarketRow(Contract("BTCUSDT", "USDT"), null, null))
            desk.update(Scan(rows, 1000, SystemClock.elapsedRealtime(), "Aktif", true), true)
            val list = activity.findViewById<ListView>(R.id.scan_list)
            assertEquals(2, list.count)
            activity.findViewById<EditText>(R.id.scan_query).setText("eth")
            assertEquals(1, list.count)
            assertEquals("ETHUSDT", (list.getItemAtPosition(0) as MarketRow).contract.symbol)
            list.performItemClick(list.adapter.getView(0, null, list), 0, 0)
            val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
            assertTrue(dialog.isShowing)
            assertTrue(dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).text.toString().contains("Hapus"))
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun tabsLeadToScannerPaperAndLocalAccount() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            activity.findViewById<View>(R.id.dash_market_summary).performClick()
            assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.page_scanner).visibility)
            assertEquals(View.GONE, activity.findViewById<View>(R.id.page_home).visibility)
            assertEquals(0, activity.findViewById<ListView>(R.id.scan_list).count)
            activity.findViewById<EditText>(R.id.scan_query).setText("ETH")
            activity.findViewById<View>(R.id.nav_paper).performClick()
            assertTrue(activity.findViewById<TextView>(R.id.paper_model).text.contains("0/30"))
            activity.findViewById<View>(R.id.nav_account).performClick()
            assertTrue(activity.findViewById<TextView>(R.id.account_wallet).text.contains("USDT"))
            assertTrue(activity.findViewById<TextView>(R.id.account_connection).text.contains("Belum terhubung"))
            activity.findViewById<View>(R.id.nav_home).performClick()
            assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.page_home).visibility)
        } finally { controller.pause().stop().destroy() }
    }
}
