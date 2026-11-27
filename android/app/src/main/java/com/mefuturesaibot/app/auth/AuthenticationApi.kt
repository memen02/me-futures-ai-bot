package com.mefuturesaibot.app.auth

// Transport contract only. Implementations must use authenticated HTTPS,
// bounded responses, no credential logging, no automatic mutation retries.
internal enum class RegistrationResult { ACCEPTED, REJECTED, UNAVAILABLE }
internal class AuthGrant(val identity: AccountIdentity, val accessToken: String,
    val lifetimeSeconds: Long) {
    override fun toString(): String = "AuthGrant(redacted)"
}
internal interface AuthenticationApi {
    fun register(username: String, password: CharArray): RegistrationResult
    fun login(username: String, password: CharArray): AuthGrant?
    fun revoke(accessToken: String)
}
