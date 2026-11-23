package com.mefuturesaibot.app.demo

import android.content.Context
import com.mefuturesaibot.app.BuildConfig
import java.security.MessageDigest

internal enum class FuturesVenue(val host: String, val preferences: String, val label: String) {
    DEMO("demo-fapi.binance.com", DemoRunner.PREFS, "Demo"),
    REAL("fapi.binance.com", "me_project_futures_real_v1", "Mainnet");

    fun prefs(context: Context) = context.getSharedPreferences(preferences, Context.MODE_PRIVATE)

    companion object {
        fun settings(context: Context) = context.getSharedPreferences("execution_mode", Context.MODE_PRIVATE)

        fun selected(context: Context): FuturesVenue = synchronized(DemoRunner.entryLock) {
            if (!BuildConfig.MAINNET_ORDERS) return@synchronized DEMO
            val settings = settings(context)
            when (settings.getString("venue",null)) {
                REAL.name -> REAL
                DEMO.name -> DEMO
                else -> {
                    // Reconcile a running legacy Real installation on its original account.
                    val real = REAL.prefs(context)
                    val initial = if (real.getBoolean("enabled",false) || real.contains("intent")) REAL else DEMO
                    check(settings.edit().putString("venue",initial.name).commit())
                    initial
                }
            }
        }

        fun select(context: Context, target: FuturesVenue): Boolean = synchronized(DemoRunner.entryLock) {
            if (target == selected(context)) return@synchronized true
            if (target == REAL && !BuildConfig.MAINNET_ORDERS) return@synchronized false
            if (values().any { venue ->
                    val ledger = venue.prefs(context)
                    ledger.getBoolean("enabled",false) || ledger.contains("intent")
                }) return@synchronized false
            // Returning to Mainnet requires a new explicit activation.
            if (!REAL.prefs(context).edit().remove("consentKey").commit()) return@synchronized false
            settings(context).edit().putString("venue",target.name).commit()
        }
        fun keyId(key: String) = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
