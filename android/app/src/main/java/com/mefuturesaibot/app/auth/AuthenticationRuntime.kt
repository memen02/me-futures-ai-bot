package com.mefuturesaibot.app.auth

import okhttp3.HttpUrl

// Create once per application process using a deployment-controlled HTTPS origin.
// Blocking operations must run off the main thread.
internal class AuthenticationRuntime(origin: HttpUrl) {
    val sessions = SecureSessionManager()
    private val transport = HttpsAuthenticationApi(origin)
    private val verified = VerifiedDeviceAuthentication(transport, KeystoreDeviceSigner)
    private val owner = AuthenticationRepository(
        RoleAuthenticationApi(transport, verified, UserRole.SUPER_ADMIN), sessions)
    private val user = AuthenticationRepository(
        RoleAuthenticationApi(transport, verified, UserRole.USER), sessions)

    @Synchronized fun login(role: UserRole, username: String, password: CharArray): Boolean =
        (if (role == UserRole.SUPER_ADMIN) owner else user).login(username, password)

    @Synchronized fun register(username: String, password: CharArray, repeated: CharArray): RegistrationResult =
        user.register(username, password, repeated)

    @Synchronized fun logout() = owner.logout()

    fun deviceCode(): String = DeviceLoginProof.digest(KeystoreDeviceSigner.publicDer())

    fun ownerPanel(activity: android.app.Activity, onError: (String) -> Unit): OwnerPanelHost {
        check(sessions.ownerToken() != null) { "OWNER session required" }
        return OwnerPanelHost(activity, sessions, transport, onError)
    }
}
