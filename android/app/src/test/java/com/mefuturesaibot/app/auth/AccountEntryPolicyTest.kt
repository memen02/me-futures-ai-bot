package com.mefuturesaibot.app.auth

import org.junit.Assert.*
import org.junit.Test

class AccountEntryPolicyTest {
    @Test fun configuredBuildRequiresAuthorization() {
        assertFalse(AccountEntryPolicy.allowsDashboard(true, false))
        assertTrue(AccountEntryPolicy.allowsDashboard(true, true))
    }
    @Test fun unconfiguredDevelopmentDashboardRemainsAvailable() {
        assertTrue(AccountEntryPolicy.allowsDashboard(false, false))
    }
    @Test fun ownerNeverBypassesAuthorization() {
        assertFalse(AccountEntryPolicy.allowsOwner(false))
        assertTrue(AccountEntryPolicy.allowsOwner(true))
    }
}
