package com.mefuturesaibot.app.auth

import java.util.Locale

internal enum class UserRole { USER, SUPER_ADMIN }
internal enum class AccountStatus { PENDING, APPROVED, SUSPENDED, REJECTED }

// Server-issued identity projection, never a local source of authorization.
internal data class AccountIdentity(val accountId: String, val username: String,
    val role: UserRole, val status: AccountStatus) {
    fun valid(): Boolean = accountId.matches(Regex("[A-Za-z0-9_-]{16,128}")) &&
        normalizeUsername(username) == username
    companion object {
        fun normalizeUsername(raw: String): String? {
            if (!raw.matches(Regex("[A-Za-z0-9_]{4,32}"))) return null
            return raw.lowercase(Locale.ROOT)
        }
    }
}
