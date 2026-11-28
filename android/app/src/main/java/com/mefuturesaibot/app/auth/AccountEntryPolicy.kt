package com.mefuturesaibot.app.auth

// Legacy development builds may continue UI testing without a backend.
// A configured build must never fall back to legacy access after auth failure.
internal object AccountEntryPolicy {
    fun allowsDashboard(configured: Boolean, authorized: Boolean): Boolean =
        !configured || authorized

    fun allowsOwner(authorized: Boolean): Boolean = authorized
}
