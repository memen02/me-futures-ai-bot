package com.mefuturesaibot.app.auth

internal enum class RegistrationError { INVALID_USERNAME, INVALID_PASSWORD, PASSWORD_MISMATCH }

// Client-side feedback only; server must independently validate and hash passwords.
// No password storage, logging, or automatic role assignment.
internal object RegistrationValidation {
    fun validate(username: String, password: CharArray, repeated: CharArray): Set<RegistrationError> {
        val errors = mutableSetOf<RegistrationError>()
        if (AccountIdentity.normalizeUsername(username) == null)
            errors.add(RegistrationError.INVALID_USERNAME)
        if (password.size !in 12..128 || password.all { it.isWhitespace() } ||
            password.any { it.isISOControl() })
            errors.add(RegistrationError.INVALID_PASSWORD)
        if (!password.contentEquals(repeated))
            errors.add(RegistrationError.PASSWORD_MISMATCH)
        return errors
    }
}
