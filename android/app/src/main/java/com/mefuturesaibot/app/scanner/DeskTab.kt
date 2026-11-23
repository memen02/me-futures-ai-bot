package com.mefuturesaibot.app.scanner

import android.os.Bundle
import com.mefuturesaibot.app.R

internal enum class DeskTab(val buttonId: Int, val pageId: Int) {
    HOME(R.id.nav_home, R.id.page_home),
    MARKET(R.id.nav_market, R.id.page_scanner),
    TRADING(R.id.nav_trading, R.id.page_trading),
    HISTORY(R.id.nav_history, R.id.page_history),
    ACCOUNT(R.id.nav_account, R.id.page_account);

    companion object {
        const val STATE_KEY = "deskDestination"
        fun restore(state: Bundle?): DeskTab {
            val name = state?.getString(STATE_KEY)
            if (name != null) return values().firstOrNull { it.name == name } ?: HOME
            return when (state?.getInt("deskTab")) {
                1 -> MARKET
                2 -> TRADING
                3 -> ACCOUNT
                else -> HOME
            }
        }
    }
}
