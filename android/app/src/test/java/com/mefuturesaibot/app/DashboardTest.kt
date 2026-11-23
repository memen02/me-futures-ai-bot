package com.mefuturesaibot.app

import android.view.View
import android.widget.TextView
import com.mefuturesaibot.app.scanner.Contract
import com.mefuturesaibot.app.scanner.DeskStore
import com.mefuturesaibot.app.exchange.Candle
import java.math.BigDecimal
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w320dp-h640dp")
class DashboardTest {
    @Test fun tokenOwnsChartAndFavoriteWhileAccountOwnsPaperStats() {
        val home = Robolectric.buildActivity(MainActivity::class.java).create()
        val app = RuntimeEnvironment.getApplication<android.app.Application>()
        val detail = Robolectric.buildActivity(TokenDetailActivity::class.java,
            TokenDetailActivity.intent(app, Contract("SOLUSDT", "USDT"))).create()
        try {
            val root = home.get().findViewById<View>(R.id.page_home)
            assertNull(root.findViewById<View>(R.id.dash_chart))
            assertNull(root.findViewById<View>(R.id.desk_risk))
            assertNull(root.findViewById<View>(R.id.desk_profit))
            assertNotNull(home.get().findViewById<View>(R.id.page_account).findViewById<View>(R.id.desk_profit))
            val activity = detail.get()
            assertTrue(activity.findViewById<TextView>(R.id.token_switch).text.contains("SOL/USDT"))
            assertNotNull(activity.findViewById<View>(R.id.dash_chart))
            activity.findViewById<View>(R.id.token_star).performClick()
            assertTrue("SOLUSDT" in DeskStore(app).favorites)
            activity.findViewById<View>(R.id.token_switch).performClick()
            assertTrue(activity.isFinishing)
            assertFalse(BotPreferences.shouldRun(activity))
        } finally { detail.destroy(); home.destroy() }
    }

    @Test fun readingUsesOnlyPrecedingBarsAndExcludesOpenCandle() {
        fun bar(i: Int, p: Int): Candle {
            val v = BigDecimal.valueOf(p.toLong())
            return Candle(i * 60_000L, v, v + BigDecimal.ONE, v - BigDecimal.ONE, v, BigDecimal.TEN)
        }
        val bars = (0..21).map { bar(it, 10 + it * 2) }
        val reading = CandleReading.at(bars, 20)!!
        assertEquals("Breakout naik", reading.label)
        assertEquals(BigDecimal("49"), reading.resistance)
        assertEquals(reading, CandleReading.at(bars.dropLast(1) + bar(21, 1_000_000), 20))
        assertNull(CandleReading.at(bars, bars.lastIndex))
    }

    @Test fun depthBridgesSnapshotAppliesDeletionsAndRejectsSequenceGaps() {
        val book = DepthBook(JSONObject("""{"T":100,"lastUpdateId":10,"bids":[["8","1"],["7","1"],["6","1"],["5","1"],["4","1"],["3","1"]],"asks":[["10","1"],["11","1"],["12","1"],["13","1"],["14","1"]]}"""))
        fun diff(first: Int, last: Int, previous: Int) = JSONObject("""{"T":101,"U":$first,"u":$last,"pu":$previous,"b":[["8","0"]],"a":[]}""")
        assertFalse(book.apply(diff(1, 9, 0)))
        assertTrue(book.apply(diff(9, 12, 8)))
        assertEquals(BigDecimal("7"), book.bids.firstKey())
        assertTrue(book.apply(diff(13, 15, 12)))
        assertThrows(IllegalArgumentException::class.java) { book.apply(diff(18, 20, 17)) }
    }
}
