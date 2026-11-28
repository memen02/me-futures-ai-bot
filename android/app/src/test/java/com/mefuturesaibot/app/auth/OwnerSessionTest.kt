package com.mefuturesaibot.app.auth

import org.junit.Assert.*
import org.junit.Test

class OwnerSessionTest {
    private val token = "a".repeat(64)
    private fun grant(role: UserRole, status: AccountStatus = AccountStatus.APPROVED,
        value: String = token) = AuthGrant(
        AccountIdentity("b".repeat(32), "tester", role, status), value, 60L)

    @Test fun ownerTokenRequiresActiveApprovedOwner() {
        val sessions = SecureSessionManager { 1000L }
        assertTrue(sessions.install(grant(UserRole.SUPER_ADMIN)))
        assertEquals(token, sessions.ownerToken())
        assertTrue(sessions.install(grant(UserRole.USER)))
        assertNull(sessions.ownerToken())
        assertFalse(sessions.install(grant(UserRole.SUPER_ADMIN, AccountStatus.SUSPENDED)))
        assertNull(sessions.ownerToken())
    }

    @Test fun ownerTokenExpiresAtExactBoundary() {
        var now = 1000L
        val sessions = SecureSessionManager { now }
        assertTrue(sessions.install(grant(UserRole.SUPER_ADMIN)))
        now = 60999L
        assertEquals(token, sessions.ownerToken())
        now = 61000L
        assertNull(sessions.ownerToken())
    }

    @Test fun ownerTokenRejectsMalformedTokenAndClearsOnLogout() {
        val sessions = SecureSessionManager { 1000L }
        sessions.install(grant(UserRole.SUPER_ADMIN, value = "z".repeat(64)))
        assertNull(sessions.ownerToken())
        assertTrue(sessions.install(grant(UserRole.SUPER_ADMIN)))
        assertEquals(token, sessions.clear())
        assertNull(sessions.ownerToken())
    }
}
