package com.mefuturesaibot.app.auth

import okhttp3.HttpUrl

// Create once per application process using a deployment-controlled HTTPS origin.
// Blocking operations must run off the main thread.
internal class AuthenticationRuntime(origin: HttpUrl) {
    val sessions = SecureSessionManager()
    val accessGate = LocalAccessGate(sessions)
    private val transport = HttpsAuthenticationApi(origin)
    private val verified = VerifiedDeviceAuthentication(transport, KeystoreDeviceSigner)
    private val owner = AuthenticationRepository(
        RoleAuthenticationApi(transport, verified, UserRole.SUPER_ADMIN), sessions)
    private val user = AuthenticationRepository(
        RoleAuthenticationApi(transport, verified, UserRole.USER), sessions)

    @Synchronized fun login(role: UserRole, username: String, password: CharArray, expected: Long = sessions.revision()): Boolean {
        accessGate.lock()
        return (if (role == UserRole.SUPER_ADMIN) owner else user).login(username, password, expected)
    }

    @Synchronized fun register(username: String, password: CharArray, repeated: CharArray): RegistrationResult =
        user.register(username, password, repeated)

    @Synchronized fun logout() {
        accessGate.lock()
        owner.logout()
    }

    // Local logout is immediate; callers revoke the returned token off the UI thread.
    fun clearLocalSession(): String? {
        accessGate.lock()
        return sessions.clear()
    }

    fun revokeSession(token: String) {
        runCatching { transport.revoke(token) }
    }

    fun deviceCode(): String = DeviceLoginProof.digest(KeystoreDeviceSigner.publicDer())

    fun ownerPanel(activity: android.app.Activity, onError: (String) -> Unit): OwnerPanelHost {
        check(accessGate.allowsOwnerScreen()) { "OWNER session required" }
        return OwnerPanelHost(activity, sessions, transport, onError)
    }
}
