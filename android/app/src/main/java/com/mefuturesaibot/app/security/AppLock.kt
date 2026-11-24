package com.mefuturesaibot.app.security

import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock

internal object AppLock {

    private const val PREFS = "me_project_security"
    private const val ENABLED = "app_lock_enabled"
    private const val AUTH_TIMEOUT_MS = 30_000L

    private var sessionGeneration = 0L

    fun sessionId(): Long = sessionGeneration

    fun invalidateUiSession() {
        sessionGeneration++
    }

    private var authenticated = false
    private var authenticatedAt = -1L

    private fun prefs(context: Context) =
        context.getSharedPreferences(
            PREFS, Context.MODE_PRIVATE
        )

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(ENABLED, false)

    fun deviceSecure(context: Context): Boolean {
        val manager =
            context.getSystemService(KeyguardManager::class.java)

        return manager?.isDeviceSecure == true
    }

    fun requiresUnlock(context: Context): Boolean =
        isEnabled(context) &&
            (!authenticated || !deviceSecure(context))

    fun markAuthenticated() {
        authenticated = true
        authenticatedAt = SystemClock.elapsedRealtime()
    }

    fun lock() {
        sessionGeneration++
        authenticated = false
        authenticatedAt = -1L
    }

    private fun recentlyAuthenticated(): Boolean {
        if (!authenticated) return false

        val age =
            SystemClock.elapsedRealtime() - authenticatedAt

        return age in 0L..AUTH_TIMEOUT_MS
    }

    fun enableAfterAuthentication(context: Context): Boolean {
        if (!recentlyAuthenticated() ||
            !deviceSecure(context)) return false

        return prefs(context).edit()
            .putBoolean(ENABLED, true)
            .commit()
    }

    fun disableAfterAuthentication(context: Context): Boolean {
        if (!recentlyAuthenticated() ||
            !deviceSecure(context)) return false

        val saved = prefs(context).edit()
            .putBoolean(ENABLED, false)
            .commit()

        if (saved) lock()

        return saved
    }
}
