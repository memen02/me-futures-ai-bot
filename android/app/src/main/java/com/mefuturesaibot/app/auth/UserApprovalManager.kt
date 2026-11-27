package com.mefuturesaibot.app.auth

// Local preflight only, not an authorization boundary. No network mutation
// until a trusted transport and server-side authorization are integrated.
internal object UserApprovalManager {
    fun eligible(actor: AccountIdentity?, target: AccountIdentity,
        desired: AccountStatus): Boolean =
        actor != null && AccountApprovalPolicy.showAdminMenu(actor) &&
            target.valid() && target.role == UserRole.USER &&
            actor.accountId != target.accountId &&
            AccountApprovalPolicy.reviewTransition(target.status, desired)
}
