package com.mefuturesaibot.app.auth

import org.junit.Assert.*
import org.junit.Test

class AuthenticationSafetyTest {
    private val user = AccountIdentity("account_1234567890","tester",UserRole.USER,AccountStatus.APPROVED)
    @Test fun registrationValidation() {
        val password = "long-password-123".toCharArray()
        assertTrue(RegistrationValidation.validate("Tester",password,password.copyOf()).isEmpty())
        assertEquals("tester",AccountIdentity.normalizeUsername("Tester"))
        assertNull(AccountIdentity.normalizeUsername("bad name"))
        assertTrue(RegistrationValidation.validate("tester",charArrayOf('x'),password).contains(RegistrationError.INVALID_PASSWORD))
        assertTrue(RegistrationValidation.validate("tester",password,charArrayOf('x')).contains(RegistrationError.PASSWORD_MISMATCH))
    }
    @Test fun sessionExpiresAndRejectsPending() {
        var now = 100L
        val sessions = SecureSessionManager { now }
        assertFalse(sessions.install(AuthGrant(user.copy(status=AccountStatus.PENDING),"a".repeat(32),60)))
        assertNull(sessions.identity())
        assertTrue(sessions.install(AuthGrant(user,"a".repeat(32),60)))
        assertEquals(user,sessions.identity())
        now += 60000
        assertNull(sessions.identity())
        assertFalse(sessions.install(AuthGrant(user,"a".repeat(32),901)))
        assertFalse(sessions.install(AuthGrant(user,"short",60)))
    }
    @Test fun clockRollbackClearsSession() {
        var now = 100L
        val sessions = SecureSessionManager { now }
        assertTrue(sessions.install(AuthGrant(user,"a".repeat(32),60)))
        now = 99L
        assertNull(sessions.identity())
    }
}
