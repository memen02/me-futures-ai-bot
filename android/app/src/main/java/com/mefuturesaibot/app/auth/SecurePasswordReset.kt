package com.mefuturesaibot.app.auth

// Client-side preflight only. Recovery proof must be issued and verified by
// the server, bound to the account, rate limited, consumed atomically once,
// and invalidate existing sessions after successful reset.
internal interface PasswordResetApi {
    fun redeem(accountId: String, proof: CharArray, newPassword: CharArray): AdminResult
}

internal object SecurePasswordReset {
    fun eligibleRequest(actor: AccountIdentity?, target: AccountIdentity): Boolean =
        actor != null && AccountApprovalPolicy.showAdminMenu(actor) &&
            target.valid() && target.role == UserRole.USER &&
            target.status == AccountStatus.APPROVED && actor.accountId != target.accountId

    fun redeem(api: PasswordResetApi, target: AccountIdentity, proof: CharArray,
        password: CharArray, repeated: CharArray): AdminResult {
        try {
            if (!target.valid() || target.status != AccountStatus.APPROVED ||
                proof.size !in 32..512 || proof.any { it.code !in 33..126 } ||
                RegistrationValidation.validate(target.username, password, repeated).isNotEmpty())
                return AdminResult.DENIED
            return api.redeem(target.accountId, proof, password)
        } finally {
            proof.fill('\u0000')
            password.fill('\u0000')
            repeated.fill('\u0000')
        }
    }
}
