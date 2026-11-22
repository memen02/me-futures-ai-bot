package com.mefuturesaibot.app.demo

import android.content.Context
import com.mefuturesaibot.app.BuildConfig
import java.security.MessageDigest

internal enum class FuturesVenue(val host: String, val preferences: String, val label: String) {
    DEMO("demo-fapi.binance.com", DemoRunner.PREFS, "Demo"),
    REAL("fapi.binance.com", "me_project_futures_real_v1", "Real");

    fun prefs(context: Context) = context.getSharedPreferences(preferences, Context.MODE_PRIVATE)

    companion object {
        val installed get() = if (BuildConfig.MAINNET_ORDERS) REAL else DEMO
        fun keyId(key: String) = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
