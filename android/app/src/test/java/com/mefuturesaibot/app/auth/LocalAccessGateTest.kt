package com.mefuturesaibot.app.auth

import org.junit.Assert.*
import org.junit.Test

class LocalAccessGateTest {
    private var now = 1000L
    private val sessions = SecureSessionManager { now }
    private val gate = LocalAccessGate(sessions)
    private fun install(role: UserRole = UserRole.USER): Long {
        assertTrue(sessions.install(AuthGrant(
            AccountIdentity("b".repeat(32), "tester", role, AccountStatus.APPROVED),
            "a".repeat(64), 60L)))
        return requireNotNull(sessions.sessionGeneration())
    }

    @Test fun loginAloneDoesNotUnlock() {
        install()
        assertFalse(gate.allowsTradingScreen())
        assertFalse(gate.allowsOwnerScreen())
    }

    @Test fun userCannotOpenOwnerPanel() {
        assertTrue(gate.unlock(install()))
        assertTrue(gate.allowsTradingScreen())
        assertFalse(gate.allowsOwnerScreen())
    }

    @Test fun ownerCanUnlockButLogoutRevokesAccess() {
        assertTrue(gate.unlock(install(UserRole.SUPER_ADMIN)))
        assertTrue(gate.allowsOwnerScreen())
        sessions.clear()
        assertFalse(gate.allowsOwnerScreen())
        assertFalse(gate.allowsTradingScreen())
    }

    @Test fun replacementSessionRejectsOldUnlock() {
        val old = install()
        assertTrue(gate.unlock(old))
        val replacement = install()
        assertFalse(gate.allowsTradingScreen())
        assertFalse(gate.unlock(old))
        assertTrue(gate.unlock(replacement))
        gate.lock()
        assertFalse(gate.allowsTradingScreen())
    }

    @Test fun expiryRevokesAccessAtBoundary() {
        assertTrue(gate.unlock(install()))
        now = 60999L
        assertTrue(gate.allowsTradingScreen())
        now = 61000L
        assertFalse(gate.allowsTradingScreen())
    }
}
