package com.mefuturesaibot.app

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w320dp-h640dp")
class DesignSystemTest {
    @Test fun dashboardThemeAndMarketRowsUseSharedTokens() {
        val app: Context = RuntimeEnvironment.getApplication()
        val context = ContextThemeWrapper(app, R.style.DashboardTheme)
        val value = TypedValue()
        assertTrue(context.theme.resolveAttribute(android.R.attr.colorAccent, value, true))
        assertEquals(Color.parseColor("#F0B90B"), value.data)
        assertEquals(Color.parseColor("#30D158"), context.getColor(R.color.me_buy))
        assertEquals(Color.parseColor("#FF453A"), context.getColor(R.color.me_sell))
        val row = LayoutInflater.from(context).inflate(R.layout.market_row, FrameLayout(context), false)
        assertTrue(row.minimumHeight >= context.resources.getDimensionPixelSize(R.dimen.me_touch_target))
        assertNotNull(row.findViewById<View>(R.id.row_price))
    }
}
