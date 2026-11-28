package com.mefuturesaibot.app.auth

import org.junit.Assert.*
import org.junit.Test

class AuthenticationRepositoryRevocationTest {
    private val identity = AccountIdentity("account_1234567890", "tester", UserRole.USER, AccountStatus.APPROVED)
    private val token = "a".repeat(64)
    private class FakeApi : AuthenticationApi {
        var offline = true
        var logins = 0
        var revokes = 0
        override fun register(username: String, password: CharArray) = RegistrationResult.REJECTED
        override fun login(username: String, password: CharArray): AuthGrant? {
            logins++
            return null
        }
        override fun revoke(accessToken: String) {
            revokes++
            if (offline) throw IllegalStateException("offline")
        }
    }
    @Test fun failedReplacementBlocksOtherRepositoryUntilRevoked() {
        val sessions = SecureSessionManager { 1000L }
        val api = FakeApi()
        val first = AuthenticationRepository(api, sessions)
        val second = AuthenticationRepository(api, sessions)
        assertTrue(sessions.install(AuthGrant(identity, token, 60)))
        assertFalse(first.login("tester", charArrayOf('x')))
        assertNull(sessions.identity())
        assertFalse(second.login("tester", charArrayOf('x')))
        assertEquals(0, api.logins)
        api.offline = false
        assertFalse(second.login("tester", charArrayOf('x')))
        assertEquals(1, api.logins)
        assertEquals(3, api.revokes)
    }
    @Test fun failedLogoutRemainsPending() {
        val sessions = SecureSessionManager { 1000L }
        val api = FakeApi()
        val repository = AuthenticationRepository(api, sessions)
        assertTrue(sessions.install(AuthGrant(identity, token, 60)))
        try {
            repository.logout()
            fail("Expected offline error")
        } catch (_: IllegalStateException) {}
        assertNull(sessions.identity())
        assertFalse(repository.login("tester", charArrayOf('x')))
        assertEquals(0, api.logins)
    }
    @Test fun cancellationDuringRevocationPreventsLogin() {
        val sessions = SecureSessionManager { 1000L }
        sessions.rememberPendingRevocation(token)
        var allowed = true
        val api = object : AuthenticationApi {
            override fun register(username: String, password: CharArray) = RegistrationResult.REJECTED
            override fun login(username: String, password: CharArray): AuthGrant? {
                throw AssertionError("Cancelled login reached transport")
            }
            override fun revoke(accessToken: String) { allowed = false }
        }
        val password = charArrayOf('x')
        val revision = sessions.revision()
        assertFalse(AuthenticationRepository(api, sessions).login("tester", password, revision) { allowed })
        assertEquals(revision, sessions.revision())
        assertTrue(password.all { it == '\u0000' })
    }
    @Test fun invalidInputDoesNotReachTransport() {
        val sessions = SecureSessionManager { 1000L }
        sessions.rememberPendingRevocation(token)
        val api = FakeApi()
        val password = charArrayOf('x')
        assertFalse(AuthenticationRepository(api, sessions).login("bad name", password))
        assertEquals(0, api.revokes)
        assertEquals(0, api.logins)
        assertTrue(password.all { it == '\u0000' })
    }
}
