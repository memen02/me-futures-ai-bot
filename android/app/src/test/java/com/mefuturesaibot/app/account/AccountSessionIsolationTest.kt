package com.mefuturesaibot.app.account

import com.mefuturesaibot.app.demo.FuturesVenue
import org.junit.Assert.*
import org.junit.Test

class AccountSessionIsolationTest {

    private val revisions =
        CredentialRevisionGuard()

    private val demo = FuturesVenue.DEMO
    private val real = FuturesVenue.REAL

    @Test
    fun activeSessionIsAccepted() {
        val gate = AccountSessionIsolation()
        val revision = revisions.current("demo")
        val token = gate.activate(demo, revision)

        assertTrue(
            gate.accepts(token, demo, revision)
        )
    }

    @Test
    fun wrongVenueIsRejected() {
        val gate = AccountSessionIsolation()
        val revision = revisions.current("demo")
        val token = gate.activate(demo, revision)

        assertFalse(
            gate.accepts(token, real, revision)
        )
    }

    @Test
    fun changedCredentialRevisionIsRejected() {
        val gate = AccountSessionIsolation()
        val old = revisions.current("demo")
        val token = gate.activate(demo, old)

        revisions.invalidate("demo")

        assertFalse(
            gate.accepts(
                token,
                demo,
                revisions.current("demo")
            )
        )
    }

    @Test
    fun replacementRejectsOldSession() {
        val gate = AccountSessionIsolation()
        val revision = revisions.current("demo")

        val old = gate.activate(demo, revision)
        val current = gate.activate(demo, revision)

        assertNotSame(old, current)

        assertFalse(
            gate.accepts(old, demo, revision)
        )

        assertTrue(
            gate.accepts(current, demo, revision)
        )
    }

    @Test
    fun switchingVenueRejectsPreviousSession() {
        val gate = AccountSessionIsolation()

        val demoRevision =
            revisions.current("demo")

        val realRevision =
            revisions.current("real")

        val old = gate.activate(
            demo,
            demoRevision
        )

        val current = gate.activate(
            real,
            realRevision
        )

        assertFalse(
            gate.accepts(old, demo, demoRevision)
        )

        assertTrue(
            gate.accepts(current, real, realRevision)
        )
    }

    @Test
    fun staleRetirementCannotRemoveNewSession() {
        val gate = AccountSessionIsolation()
        val revision = revisions.current("demo")

        val old = gate.activate(demo, revision)
        val current = gate.activate(demo, revision)

        assertFalse(gate.retire(old))

        assertTrue(
            gate.accepts(current, demo, revision)
        )
    }

    @Test
    fun retirementRejectsActiveToken() {
        val gate = AccountSessionIsolation()
        val revision = revisions.current("demo")
        val token = gate.activate(demo, revision)

        assertTrue(gate.retire(token))
        assertFalse(gate.retire(token))

        assertFalse(
            gate.accepts(token, demo, revision)
        )
    }

    @Test
    fun invalidationRejectsCurrentSession() {
        val gate = AccountSessionIsolation()
        val revision = revisions.current("demo")
        val token = gate.activate(demo, revision)

        gate.invalidate()

        assertFalse(
            gate.accepts(token, demo, revision)
        )
    }

    @Test
    fun invalidationAllowsFreshActivation() {
        val gate = AccountSessionIsolation()
        val revision = revisions.current("demo")

        val old = gate.activate(demo, revision)

        gate.invalidate()

        val current = gate.activate(demo, revision)

        assertFalse(
            gate.accepts(old, demo, revision)
        )

        assertTrue(
            gate.accepts(current, demo, revision)
        )
    }

    @Test
    fun closedGateRejectsAllTokens() {
        val gate = AccountSessionIsolation()
        val revision = revisions.current("demo")
        val token = gate.activate(demo, revision)

        gate.close()
        gate.close()

        assertFalse(
            gate.accepts(token, demo, revision)
        )

        assertFalse(gate.retire(token))
    }

    @Test
    fun closedGateCannotBeReactivated() {
        val gate = AccountSessionIsolation()
        val revision = revisions.current("demo")

        gate.close()
        gate.invalidate()

        assertThrows(
            IllegalStateException::class.java
        ) {
            gate.activate(demo, revision)
        }
    }

    @Test
    fun tokensCannotCrossGateInstances() {
        val first = AccountSessionIsolation()
        val second = AccountSessionIsolation()
        val revision = revisions.current("demo")

        val token = first.activate(demo, revision)

        second.activate(demo, revision)

        assertFalse(
            second.accepts(token, demo, revision)
        )
    }
}
