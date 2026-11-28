package com.mefuturesaibot.app.auth

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class ApplicationAuthenticationTest {
    @Test fun unconfiguredRuntimeDeniesAccess() {
        assertNull(ApplicationAuthentication.current())
        assertFalse(ApplicationAuthentication.allowsTradingScreen())
        assertFalse(ApplicationAuthentication.allowsOwnerScreen())
        ApplicationAuthentication.lockLocalAccess()
        assertFalse(ApplicationAuthentication.allowsTradingScreen())
    }

    @Test fun invalidOriginDoesNotInstallRuntime() {
        val invalid = listOf("http://example.com/", "https://example.com/path",
            "https://user:password@example.com/", "https://example.com/?x=1",
            "https://example.com/#fragment")
        for (origin in invalid) {
            try {
                ApplicationAuthentication.initialize(origin.toHttpUrl())
                fail("Invalid origin accepted")
            } catch (_: IllegalArgumentException) {
                assertNull(ApplicationAuthentication.current())
                assertFalse(ApplicationAuthentication.allowsTradingScreen())
                assertFalse(ApplicationAuthentication.allowsOwnerScreen())
            }
        }
    }
}
