package com.mefuturesaibot.app.account

import org.junit.Assert.*
import org.junit.Test

class CredentialRevisionGuardTest {

    @Test
    fun unchangedIdentityKeepsRevision() {
        val guard = CredentialRevisionGuard()

        val revision = guard.current("demo")

        assertSame(
            revision,
            guard.current("demo")
        )

        assertTrue(
            guard.isCurrent("demo", revision)
        )
    }

    @Test
    fun invalidationRejectsPreviousRevision() {
        val guard = CredentialRevisionGuard()

        val old = guard.current("demo")

        guard.invalidate("demo")

        val current = guard.current("demo")

        assertNotSame(old, current)

        assertFalse(
            guard.isCurrent("demo", old)
        )

        assertTrue(
            guard.isCurrent("demo", current)
        )
    }

    @Test
    fun demoAndRealRemainIndependent() {
        val guard = CredentialRevisionGuard()

        val demo = guard.current("demo")
        val real = guard.current("real")

        assertNotSame(demo, real)

        guard.invalidate("demo")

        assertFalse(
            guard.isCurrent("demo", demo)
        )

        assertTrue(
            guard.isCurrent("real", real)
        )
    }

    @Test
    fun oldRevisionNeverBecomesCurrentAgain() {
        val guard = CredentialRevisionGuard()

        val old = guard.current("demo")

        repeat(1000) {
            guard.invalidate("demo")

            assertFalse(
                guard.isCurrent("demo", old)
            )
        }
    }

    @Test
    fun revisionCannotBeUsedForAnotherIdentity() {
        val guard = CredentialRevisionGuard()

        val revision = guard.current("demo")

        assertFalse(
            guard.isCurrent("real", revision)
        )
    }

    @Test
    fun separateRegistriesDoNotShareRevisions() {
        val first = CredentialRevisionGuard()
        val second = CredentialRevisionGuard()

        assertFalse(
            second.isCurrent(
                "demo",
                first.current("demo")
            )
        )
    }

    @Test
    fun blankIdentityIsRejected() {
        val guard = CredentialRevisionGuard()

        assertThrows(
            IllegalArgumentException::class.java
        ) {
            guard.current("")
        }

        assertThrows(
            IllegalArgumentException::class.java
        ) {
            guard.invalidate(" ")
        }
    }
}
