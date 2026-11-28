package com.mefuturesaibot.app.auth

import android.os.SystemClock

// Memory-only access token. Process restart requires login. Server remains
// authoritative for expiry, revocation and role checks on every operation.
internal class SecureSessionManager(private val clock: () -> Long = { SystemClock.elapsedRealtime() }) {
    private var grant: AuthGrant? = null
    private var issuedAt = 0L
    private var generation = 0L
    private val pendingRevocations = linkedSetOf<String>()
    @Synchronized fun rememberPendingRevocation(token: String) {
        pendingRevocations.add(token)
    }
    @Synchronized fun confirmRevocation(token: String) {
        pendingRevocations.remove(token)
    }
    fun flushPendingRevocation(revoke: (String) -> Unit): Boolean {
        val tokens = synchronized(this) { pendingRevocations.toList() }
        for (token in tokens) {
            try { revoke(token) } catch (_: Exception) { return false }
            confirmRevocation(token)
        }
        return synchronized(this) { pendingRevocations.isEmpty() }
    }
    private var grantOwner: Any? = null
    @Synchronized fun clearOwnedGrant(owner: Any): String? =
        if (grantOwner === owner) clear() else null
    @Synchronized fun sessionGeneration(): Long? = if (active() != null) generation else null
    @Synchronized fun withSessionLock(action: () -> Unit) = action()
    @Synchronized fun revision(): Long = generation
    @Synchronized fun beginLogin(expected: Long): Pair<String?, Long>? {
        if (generation != expected) return null
        val previous = clear()
        return previous to generation
    }
    @Synchronized fun installIfUnchanged(value: AuthGrant, expected: Long, owner: Any? = null, allowed: () -> Boolean = { true }): Boolean {
        if (generation != expected || !allowed()) return false
        if (!install(value)) return false
        grantOwner = owner
        return true
    }
    @Synchronized fun install(value: AuthGrant): Boolean {
        clear()
        if (!AccountApprovalPolicy.showTrading(value.identity) ||
            value.lifetimeSeconds !in 1L..900L ||
            value.accessToken.length !in 32..4096 ||
            value.accessToken.any { it.code !in 33..126 }) return false
        val now = clock()
        if (now < 0) return false
        issuedAt = now
        grant = value
        return true
    }
    @Synchronized private fun active(): AuthGrant? {
        val value = grant ?: return null
        val now = clock()
        if (now < issuedAt || now-issuedAt >= value.lifetimeSeconds*1000L) {
            clear()
            return null
        }
        return value
    }
    @Synchronized fun identity(): AccountIdentity? = active()?.identity
    @Synchronized fun ownerToken(): String? {
        val value = active() ?: return null
        if (value.identity.role != UserRole.SUPER_ADMIN ||
            value.identity.status != AccountStatus.APPROVED ||
            !value.accessToken.matches(Regex("[0-9a-f]{64}"))) return null
        return value.accessToken
    }
    @Synchronized fun clear(): String? {
        generation++
        val token = grant?.accessToken
        grant = null
        grantOwner = null
        issuedAt = 0L
        return token
    }
}
