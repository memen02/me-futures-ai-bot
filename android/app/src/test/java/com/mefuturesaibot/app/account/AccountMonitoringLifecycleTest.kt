package com.mefuturesaibot.app.account

import com.mefuturesaibot.app.demo.FuturesVenue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class AccountMonitoringLifecycleTest {

    private val revisions = CredentialRevisionGuard()
    private val demo = FuturesVenue.DEMO
    private val real = FuturesVenue.REAL

    private fun revision(venue: FuturesVenue) =
        revisions.current(venue.name)

    private class Resource(
        private val onClose: () -> Unit = {}
    ) : AutoCloseable {

        var closes = 0
            private set

        override fun close() {
            closes++
            onClose()
        }
    }

    @Test
    fun activationAcceptsCurrentSession() {
        val resource = Resource()

        val lifecycle = AccountMonitoringLifecycle(
            ::revision,
            { resource }
        )

        val token = lifecycle.activate(
            demo, revision(demo)
        )!!

        assertTrue(lifecycle.accepts(token))
        assertTrue(lifecycle.refresh())

        lifecycle.close()

        assertEquals(1, resource.closes)
        assertFalse(lifecycle.accepts(token))
    }

    @Test
    fun replacementClosesOldBeforeCreatingNew() {
        val resources = mutableListOf<Resource>()

        val lifecycle = AccountMonitoringLifecycle(
            ::revision,
            {
                if (resources.isNotEmpty()) {
                    assertEquals(
                        1,
                        resources.last().closes
                    )
                }

                Resource().also(resources::add)
            }
        )

        val old = lifecycle.activate(
            demo, revision(demo)
        )!!

        val current = lifecycle.activate(
            real, revision(real)
        )!!

        assertFalse(lifecycle.accepts(old))
        assertTrue(lifecycle.accepts(current))
        assertEquals(1, resources.first().closes)

        lifecycle.close()

        assertEquals(1, resources.last().closes)
    }

    @Test
    fun changedCredentialsRejectAndRetireSession() {
        val resource = Resource()

        val lifecycle = AccountMonitoringLifecycle(
            ::revision,
            { resource }
        )

        val token = lifecycle.activate(
            demo, revision(demo)
        )!!

        revisions.invalidate(demo.name)

        assertFalse(lifecycle.accepts(token))
        assertFalse(lifecycle.refresh())
        assertEquals(1, resource.closes)

        lifecycle.close()

        assertEquals(1, resource.closes)
    }

    @Test
    fun staleRevisionCannotCreateResource() {
        var creates = 0

        val lifecycle = AccountMonitoringLifecycle(
            ::revision,
            {
                creates++
                Resource()
            }
        )

        val old = revision(demo)

        revisions.invalidate(demo.name)

        assertNull(lifecycle.activate(demo, old))
        assertEquals(0, creates)

        lifecycle.close()
    }

    @Test
    fun mutationDuringCreationRetiresResource() {
        val resource = Resource()

        val lifecycle = AccountMonitoringLifecycle(
            ::revision,
            {
                revisions.invalidate(demo.name)
                resource
            }
        )

        assertNull(
            lifecycle.activate(demo, revision(demo))
        )

        assertEquals(1, resource.closes)
        assertFalse(lifecycle.refresh())

        lifecycle.close()
    }

    @Test
    fun stopRejectsOldTokenAndAllowsRestart() {
        val lifecycle = AccountMonitoringLifecycle(
            ::revision,
            { Resource() }
        )

        val old = lifecycle.activate(
            demo, revision(demo)
        )!!

        lifecycle.stop()
        lifecycle.stop()

        val current = lifecycle.activate(
            demo, revision(demo)
        )!!

        assertFalse(lifecycle.accepts(old))
        assertTrue(lifecycle.accepts(current))

        lifecycle.close()
    }

    @Test
    fun identityRetiredBeforeResourceClose() {
        lateinit var lifecycle: AccountMonitoringLifecycle
        lateinit var token: AccountSessionIsolation.Token

        val resource = Resource {
            assertFalse(lifecycle.accepts(token))
        }

        lifecycle = AccountMonitoringLifecycle(
            ::revision,
            { resource }
        )

        token = lifecycle.activate(
            demo, revision(demo)
        )!!

        lifecycle.stop()

        assertEquals(1, resource.closes)

        lifecycle.close()
    }

    @Test
    fun factoryFailureLeavesNoActiveSession() {
        lateinit var token: AccountSessionIsolation.Token

        val lifecycle = AccountMonitoringLifecycle(
            ::revision,
            {
                token = it
                throw IllegalStateException("Test")
            }
        )

        assertThrows(IllegalStateException::class.java) {
            lifecycle.activate(demo, revision(demo))
        }

        assertFalse(lifecycle.accepts(token))
        assertFalse(lifecycle.refresh())

        lifecycle.close()
    }

    @Test
    fun closeFailurePreventsReplacement() {
        var creates = 0

        val lifecycle = AccountMonitoringLifecycle(
            ::revision,
            {
                creates++
                Resource {
                    throw IllegalStateException("Test")
                }
            }
        )

        val token = lifecycle.activate(
            demo, revision(demo)
        )!!

        assertThrows(IllegalStateException::class.java) {
            lifecycle.activate(real, revision(real))
        }

        assertEquals(1, creates)
        assertFalse(lifecycle.accepts(token))

        assertThrows(IllegalStateException::class.java) {
            lifecycle.activate(demo, revision(demo))
        }

        lifecycle.close()
    }

    @Test
    fun otherVenueMutationDoesNotRetireSession() {
        val lifecycle = AccountMonitoringLifecycle(
            ::revision,
            { Resource() }
        )

        val token = lifecycle.activate(
            demo, revision(demo)
        )!!

        revisions.invalidate(real.name)

        assertTrue(lifecycle.accepts(token))
        assertTrue(lifecycle.refresh())

        lifecycle.close()
    }

    @Test
    fun closedLifecycleCannotRestart() {
        val lifecycle = AccountMonitoringLifecycle(
            ::revision,
            { Resource() }
        )

        lifecycle.close()
        lifecycle.close()

        assertThrows(IllegalStateException::class.java) {
            lifecycle.activate(demo, revision(demo))
        }
    }

    @Test
    fun wrongThreadCannotMutateLifecycle() {
        val lifecycle = AccountMonitoringLifecycle(
            ::revision,
            { Resource() }
        )

        val token = lifecycle.activate(
            demo, revision(demo)
        )!!

        val executor = Executors.newSingleThreadExecutor()

        try {
            val result = executor.submit<Boolean> {
                try {
                    lifecycle.stop()
                    false
                } catch (_: IllegalStateException) {
                    true
                }
            }

            assertTrue(result.get(5, TimeUnit.SECONDS))
            assertTrue(lifecycle.accepts(token))
        } finally {
            executor.shutdownNow()
            lifecycle.close()
        }
    }

    @Test
    fun reentrantMutationDuringCreationIsRejected() {
        lateinit var lifecycle: AccountMonitoringLifecycle

        lifecycle = AccountMonitoringLifecycle(
            ::revision,
            {
                assertThrows(IllegalStateException::class.java) {
                    lifecycle.stop()
                }

                Resource()
            }
        )

        val token = lifecycle.activate(
            demo, revision(demo)
        )!!

        assertTrue(lifecycle.accepts(token))

        lifecycle.close()
    }

    @Test
    fun revisionLookupFailureRejectsActiveSession() {
        var fail = false
        val resource = Resource()

        val lifecycle = AccountMonitoringLifecycle(
            {
                check(!fail)
                revision(it)
            },
            { resource }
        )

        val token = lifecycle.activate(
            demo, revision(demo)
        )!!

        fail = true

        assertFalse(lifecycle.accepts(token))
        assertFalse(lifecycle.refresh())
        assertEquals(1, resource.closes)

        lifecycle.close()
    }
}
