package com.mefuturesaibot.app

import android.content.Context

object BotPreferences {
    private const val PREFS_NAME = "me_futures_settings"
    private const val KEY_AUTO_START = "auto_start_enabled"
    private const val KEY_SHOULD_RUN = "should_run"

    fun isAutoStartEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_START, false)
    }

    fun setAutoStartEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_START, enabled)
            .apply()
    }

    fun shouldRun(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOULD_RUN, false)
    }

    fun setShouldRun(context: Context, shouldRun: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOULD_RUN, shouldRun)
            .apply()
    }
}
