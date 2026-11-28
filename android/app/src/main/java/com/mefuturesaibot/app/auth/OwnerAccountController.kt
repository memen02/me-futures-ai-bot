package com.mefuturesaibot.app.auth

import org.json.JSONObject

// Blocking transport: callers must use a background worker.
// Server authorization remains mandatory for every request.
internal class OwnerAccountController(
    private val sessions: SecureSessionManager,
    private val api: HttpsAuthenticationApi
) {
    private fun <T> authorized(action: (String) -> T): T {
        val token = sessions.ownerToken()
            ?: throw SecurityException("OWNER session required")
        val result = action(token)
        if (sessions.ownerToken() != token)
            throw SecurityException("OWNER session changed; refresh account state")
        return result
    }

    fun listUsers(after: String? = null): JSONObject =
        authorized { api.listUsers(it, after) }

    fun review(target: String, expected: AccountStatus, desired: AccountStatus): Boolean {
        require(AccountApprovalPolicy.reviewTransition(expected, desired))
        return authorized { api.reviewUser(it, target, expected, desired) }
    }

    fun grantLicense(target: String, kind: String, expires: Long?): Boolean =
        authorized { api.grantLicense(it, target, kind, expires) }

    fun revokeLicense(target: String): Boolean =
        authorized { api.revokeLicense(it, target) }

    fun bindDevice(target: String, digest: String, expected: String?): Boolean =
        authorized { api.bindDevice(it, target, digest, expected) }
}
