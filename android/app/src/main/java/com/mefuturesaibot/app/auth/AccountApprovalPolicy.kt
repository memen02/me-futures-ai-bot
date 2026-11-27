package com.mefuturesaibot.app.auth

// UI projection only. All privileged operations require independent server authorization.
// Registration always starts as USER/PENDING on the server.
internal object AccountApprovalPolicy {
    fun showTrading(account: AccountIdentity?): Boolean =
        account != null && account.valid() && account.status == AccountStatus.APPROVED

    fun showAdminMenu(account: AccountIdentity?): Boolean =
        showTrading(account) && account?.role == UserRole.SUPER_ADMIN

    fun reviewTransition(from: AccountStatus, to: AccountStatus): Boolean = when (from) {
        AccountStatus.PENDING -> to == AccountStatus.APPROVED || to == AccountStatus.REJECTED
        AccountStatus.APPROVED -> to == AccountStatus.SUSPENDED
        AccountStatus.SUSPENDED -> to == AccountStatus.APPROVED
        AccountStatus.REJECTED -> false
    }
}
