package com.mefuturesaibot.app.auth

import android.app.Activity

internal class AuthenticatedNavigation(
    activity: Activity,
    private val sessions: SecureSessionManager
) : AutoCloseable {
    private val unlock = LocalUnlock(activity)

    fun open(onOwner: () -> Unit, onUser: () -> Unit, onDenied: () -> Unit) {
        val generation = sessions.sessionGeneration()
        val before = sessions.identity()
        if (generation == null || !AccountApprovalPolicy.showTrading(before)) {
            onDenied()
            return
        }
        unlock.authenticate { success ->
            val current = sessions.identity()
            if (!success || sessions.sessionGeneration() != generation || current != before || !AccountApprovalPolicy.showTrading(current)) {
                onDenied()
            } else if (AccountApprovalPolicy.showAdminMenu(current)) {
                if (sessions.ownerToken() != null) onOwner() else onDenied()
            } else {
                onUser()
            }
        }
    }

    override fun close() = unlock.close()
}
