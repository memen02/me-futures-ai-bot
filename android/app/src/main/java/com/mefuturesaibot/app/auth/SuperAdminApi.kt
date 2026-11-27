package com.mefuturesaibot.app.auth

internal enum class AdminResult { APPLIED, DENIED, CONFLICT, UNAVAILABLE }

// Server must authorize the bearer token, enforce USER-only targets,
// compare expected status atomically, revoke affected sessions and audit.
// No automatic retries after ambiguous responses.
internal interface SuperAdminApi {
    fun review(accessToken: String, targetId: String, expected: AccountStatus,
        desired: AccountStatus): AdminResult
    // Server delivers a short-lived single-use reset credential via a verified
    // recovery channel. Never return the user's password or reset secret to admin.
    fun requestReset(accessToken: String, targetId: String): AdminResult
}
