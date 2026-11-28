package com.mefuturesaibot.app.auth

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AuthenticationRuntimeInputTest {
    @Test fun invalidRequestsPreserveApprovedSessionAndErasePassword() {
        val runtime = AuthenticationRuntime("https://test.invalid/".toHttpUrl())
        val identity = AccountIdentity("account_1234567890", "tester", UserRole.USER, AccountStatus.APPROVED)
        assertTrue(runtime.sessions.install(AuthGrant(identity, "a".repeat(64), 60)))
        val revision = runtime.sessions.revision()
        assertTrue(runtime.accessGate.unlock(revision))
        try {
            val cases = listOf(
                Triple("bad name", charArrayOf('x'), revision),
                Triple("tester", charArrayOf(), revision),
                Triple("tester", CharArray(129) { 'x' }, revision),
                Triple("tester", charArrayOf('x'), revision - 1)
            )
            for ((name, password, expected) in cases) {
                assertFalse(runtime.login(UserRole.USER, name, password, expected))
                assertTrue(password.all { it == '\u0000' })
                assertEquals(revision, runtime.sessions.revision())
                assertEquals(identity, runtime.sessions.identity())
                assertTrue(runtime.accessGate.allowsTradingScreen())
                assertFalse(runtime.accessGate.allowsOwnerScreen())
            }
        } finally {
            runtime.clearLocalSession()
        }
    }
}
