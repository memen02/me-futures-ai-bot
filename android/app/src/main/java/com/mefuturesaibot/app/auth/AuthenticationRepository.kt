package com.mefuturesaibot.app.auth

// Blocking API: call from a dedicated background executor, never UI thread.
// Serializes auth operations per repository. No automatic retries.
internal class AuthenticationRepository(private val api: AuthenticationApi,
    private val sessions: SecureSessionManager) {
    @Synchronized fun register(username: String, password: CharArray,
        repeated: CharArray): RegistrationResult {
        try {
            if (RegistrationValidation.validate(username,password,repeated).isNotEmpty())
                return RegistrationResult.REJECTED
            return api.register(AccountIdentity.normalizeUsername(username)!!,password)
        } finally {
            password.fill('\u0000')
            repeated.fill('\u0000')
        }
    }
    @Synchronized fun login(username: String, password: CharArray, expected: Long = sessions.revision(), owner: Any? = null, allowed: () -> Boolean = { true }): Boolean {
        try {
            // Fail closed if the previous server session cannot be revoked.
            if (!allowed()) return false
            val normalized = AccountIdentity.normalizeUsername(username) ?: return false
            if (password.size !in 1..128) return false
            if (!sessions.flushPendingRevocation(api::revoke) || !allowed()) return false
            val (previous, revision) = sessions.beginLogin(expected) ?: return false
            previous?.let { token ->
                try { api.revoke(token) } catch (_: Exception) { return false }
                sessions.confirmRevocation(token)
            }
            if (!allowed()) return false
            val grant = api.login(normalized,password) ?: return false
            if (grant.identity.username != normalized || !sessions.installIfUnchanged(grant, revision, owner, allowed)) {
                sessions.rememberPendingRevocation(grant.accessToken)
                try {
                    api.revoke(grant.accessToken)
                    sessions.confirmRevocation(grant.accessToken)
                } catch (_: Exception) { /* retained for retry */ }
                return false
            }
            return true
        } finally { password.fill('\u0000') }
    }
    @Synchronized fun logout() {
        sessions.clearAndRemember()?.let { token ->
            api.revoke(token)
            sessions.confirmRevocation(token)
        }
    }
}
