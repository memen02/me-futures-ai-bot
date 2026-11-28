package com.mefuturesaibot.app.auth

import okhttp3.HttpUrl

// Process-wide runtime. Initialize from trusted deployment configuration only,
// never from a user-supplied URL or an incoming Intent.
internal object ApplicationAuthentication {
    private var runtime: AuthenticationRuntime? = null
    private var configuredOrigin: HttpUrl? = null

    @Synchronized fun initialize(origin: HttpUrl): AuthenticationRuntime {
        runtime?.let {
            check(configuredOrigin == origin) { "Authentication origin cannot change" }
            return it
        }
        val created = AuthenticationRuntime(origin)
        configuredOrigin = origin
        runtime = created
        return created
    }

    @Synchronized fun current(): AuthenticationRuntime? = runtime

    fun allowsTradingScreen(): Boolean =
        current()?.accessGate?.allowsTradingScreen() == true

    fun allowsOwnerScreen(): Boolean =
        current()?.accessGate?.allowsOwnerScreen() == true

    fun lockLocalAccess() {
        current()?.accessGate?.lock()
    }
}
