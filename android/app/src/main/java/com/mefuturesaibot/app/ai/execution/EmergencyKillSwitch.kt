package com.mefuturesaibot.app.ai.execution

import android.content.Context
import com.mefuturesaibot.app.demo.FuturesVenue

// Persistent, default-deny entry latch. Does not cancel protective orders.
// Integration must serialize entry authorization with stop requests.
internal class EmergencyKillSwitch(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ai_entry_latch_v1",Context.MODE_PRIVATE)
    private fun key(venue: FuturesVenue, accountId: String): String {
        require(accountId.matches(Regex("[a-f0-9]{64}")))
        return "${venue.name}:$accountId"
    }
    fun halt(venue: FuturesVenue, accountId: String) {
        check(prefs.edit().putBoolean(key(venue,accountId),false).commit())
    }
    fun entryEnabled(venue: FuturesVenue, accountId: String): Boolean =
        venue == FuturesVenue.DEMO && prefs.getBoolean(key(venue,accountId),false)

    // Explicit operator request after reconciliation; no automatic reset.
    fun armDemo(accountId: String, explicitRequest: Boolean, reconciliationComplete: Boolean): Boolean {
        if (!explicitRequest || !reconciliationComplete) return false
        return prefs.edit().putBoolean(key(FuturesVenue.DEMO,accountId),true).commit()
    }
}
