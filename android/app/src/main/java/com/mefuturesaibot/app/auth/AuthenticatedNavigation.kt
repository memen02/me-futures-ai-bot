package com.mefuturesaibot.app.auth

import android.app.Activity

internal class AuthenticatedNavigation(
    private val activity: Activity,
    private val sessions: SecureSessionManager,
    private val accessGate: LocalAccessGate
) : AutoCloseable {
    private val unlock = LocalUnlock(activity)

    fun open(onOwner: () -> Unit, onUser: () -> Unit, onDenied: () -> Unit) {
        accessGate.lock()
        val generation = sessions.sessionGeneration()
        val before = sessions.identity()
        if (generation == null || !AccountApprovalPolicy.showTrading(before)) {
            onDenied()
            return
        }
        unlock.authenticate { success ->
            val current = sessions.identity()
            if (!success || (activity as? com.mefuturesaibot.app.security.SecureActivity)?.canUseProtectedUi() != true || sessions.sessionGeneration() != generation || current != before || !AccountApprovalPolicy.showTrading(current)) {
                onDenied()
            } else if (!accessGate.unlock(generation)) {
                onDenied()
            } else if (AccountApprovalPolicy.showAdminMenu(current)) {
                if (accessGate.allowsOwnerScreen()) onOwner() else onDenied()
            } else {
                if (accessGate.allowsTradingScreen()) onUser() else onDenied()
            }
        }
    }

    override fun close() = unlock.close()
}
