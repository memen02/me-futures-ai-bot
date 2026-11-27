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
    @Synchronized fun login(username: String, password: CharArray): Boolean {
        try {
            sessions.clear()?.let { api.revoke(it) }
            val normalized = AccountIdentity.normalizeUsername(username) ?: return false
            if (password.size !in 1..128) return false
            val grant = api.login(normalized,password) ?: return false
            if (grant.identity.username != normalized || !sessions.install(grant)) {
                api.revoke(grant.accessToken)
                return false
            }
            return true
        } finally { password.fill('\u0000') }
    }
    @Synchronized fun logout() {
        sessions.clear()?.let { api.revoke(it) }
    }
}
