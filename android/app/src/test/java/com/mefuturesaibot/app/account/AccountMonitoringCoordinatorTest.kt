package com.mefuturesaibot.app.account

import com.mefuturesaibot.app.BuildConfig
import com.mefuturesaibot.app.demo.FuturesVenue
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountMonitoringCoordinatorTest {
    private val revisions = CredentialRevisionGuard()
    private val venue = FuturesVenue.DEMO
    private fun revision(target: FuturesVenue) = revisions.current(target.name)
    private fun credentials(target: FuturesVenue = venue) =
        CredentialRevisionSnapshot(BinanceCredentials("fake-key", "fake-secret"), revision(target))

    // Block both REST consumers before starting. No exchange requests.
    private val guard = AccountRateLimitGuard(elapsed = { 0L }, wallClock = { 0L })
        .also { it.blockFor(300000L) }

    private fun coordinator() = AccountMonitoringCoordinator(
        currentRevision = ::revision,
        scope = AccountMonitoringRateLimitScope(guard),
        elapsed = { 0L }
    )

    @Test fun staleCredentialsCannotStart() {
        val old = credentials()
        revisions.invalidate(venue.name)
        coordinator().use {
            assertFalse(it.start(venue, old))
            assertNull(it.snapshot())
        }
    }

    @Test fun cooldownBlocksConnectionAndHidesBalance() {
        coordinator().use {
            assertTrue(it.start(venue, credentials()))
            val state = checkNotNull(it.snapshot())
            assertEquals(venue, state.venue)
            assertEquals(AccountSessionState.RETRY_WAIT, state.session.state)
            assertNull(state.balance)
        }
    }

    @Test fun credentialMutationHidesAndRetiresSession() {
        coordinator().use {
            assertTrue(it.start(venue, credentials()))
            revisions.invalidate(venue.name)
            assertNull(it.snapshot())
            assertFalse(it.refresh())
            assertTrue(it.start(venue, credentials()))
            assertNotNull(it.snapshot())
        }
    }

    @Test fun otherVenueMutationDoesNotRetireDemo() {
        coordinator().use {
            assertTrue(it.start(venue, credentials()))
            revisions.invalidate(FuturesVenue.REAL.name)
            assertTrue(it.refresh())
            assertEquals(venue, it.snapshot()?.venue)
        }
    }

    @Test fun stopAndCloseHideAllAccountData() {
        coordinator().use {
            assertTrue(it.start(venue, credentials()))
            it.stop()
            assertNull(it.snapshot())
            assertFalse(it.refresh())
            assertTrue(it.start(venue, credentials()))
            it.close()
            assertNull(it.snapshot())
            assertFalse(it.refresh())
        }
    }

    @Test fun devRejectsRealVenue() {
        if (BuildConfig.MAINNET_ORDERS) return
        coordinator().use {
            assertThrows(IllegalArgumentException::class.java) {
                it.start(FuturesVenue.REAL, credentials(FuturesVenue.REAL))
            }
            assertNull(it.snapshot())
        }
    }

    @Test fun closedCoordinatorCannotRestart() {
        coordinator().use {
            it.close()
            assertThrows(IllegalStateException::class.java) {
                it.start(venue, credentials())
            }
            assertNull(it.snapshot())
        }
    }

    @Test fun staleReplacementRetiresPreviousSession() {
        coordinator().use {
            val old = credentials()
            assertTrue(it.start(venue, old))
            revisions.invalidate(venue.name)
            assertFalse(it.start(venue, old))
            assertNull(it.snapshot())
            assertFalse(it.refresh())
        }
    }

    @Test fun realAndDemoRevisionsRemainIsolated() {
        if (!BuildConfig.MAINNET_ORDERS) return
        coordinator().use {
            val real = FuturesVenue.REAL
            assertTrue(it.start(venue, credentials()))
            assertTrue(it.start(real, credentials(real)))
            revisions.invalidate(venue.name)
            assertTrue(it.refresh())
            assertEquals(real, it.snapshot()?.venue)
            assertNull(it.snapshot()?.balance)
            revisions.invalidate(real.name)
            assertNull(it.snapshot())
            assertFalse(it.refresh())
        }
    }

    @Test fun repeatedStopDoesNotPreventFreshStart() {
        coordinator().use {
            assertTrue(it.start(venue, credentials()))
            repeat(3) { _ -> it.stop() }
            assertNull(it.snapshot())
            assertTrue(it.start(venue, credentials()))
            assertNull(it.snapshot()?.balance)
        }
    }
}
