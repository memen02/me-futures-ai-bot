package com.mefuturesaibot.app.auth

// UI access only. Not authorization for exchange orders or backend mutations.
internal class LocalAccessGate(private val sessions: SecureSessionManager) {
    private var unlockedGeneration: Long? = null

    @Synchronized fun unlock(expectedGeneration: Long): Boolean {
        unlockedGeneration = null
        if (sessions.sessionGeneration() != expectedGeneration) return false
        unlockedGeneration = expectedGeneration
        return true
    }

    @Synchronized fun lock() {
        unlockedGeneration = null
    }

    @Synchronized fun allowsTradingScreen(): Boolean {
        val generation = unlockedGeneration ?: return false
        return sessions.sessionGeneration() == generation &&
            AccountApprovalPolicy.showTrading(sessions.identity()) &&
            sessions.sessionGeneration() == generation
    }

    @Synchronized fun allowsOwnerScreen(): Boolean {
        val generation = unlockedGeneration ?: return false
        return allowsTradingScreen() && sessions.ownerToken() != null &&
            sessions.sessionGeneration() == generation
    }
}
