package com.mefuturesaibot.app.auth

// Explicit login route selection; never fall back from device proof to password-only.
internal class RoleAuthenticationApi(
    private val owner: AuthenticationApi,
    private val user: AuthenticationApi,
    private val role: UserRole
) : AuthenticationApi {
    override fun register(username: String, password: CharArray): RegistrationResult =
        user.register(username, password)

    override fun login(username: String, password: CharArray): AuthGrant? {
        val api = if (role == UserRole.SUPER_ADMIN) owner else user
        val grant = api.login(username, password) ?: return null
        if (grant.identity.role != role || grant.identity.status != AccountStatus.APPROVED ||
            grant.identity.username != username || !grant.identity.valid() ||
            grant.lifetimeSeconds !in 1L..900L) {
            api.revoke(grant.accessToken)
            return null
        }
        return grant
    }

    override fun revoke(accessToken: String) {
        // Both routes use the same authoritative backend session store.
        owner.revoke(accessToken)
    }
}
