package com.mefuturesaibot.app.auth

internal class DeviceChallenge(val accountId: String, val nonce: String)
internal interface VerifiedDeviceTransport {
    fun register(username: String, password: CharArray): RegistrationResult
    fun challenge(username: String, password: CharArray, keyDigest: String): DeviceChallenge?
    fun login(username: String, password: CharArray, keyDigest: String,
        nonce: String, publicDer: ByteArray, signature: ByteArray): AuthGrant?
    fun revoke(accessToken: String)
}
internal interface DeviceSigner {
    fun publicDer(): ByteArray
    fun sign(accountId: String, digest: String, nonce: String): ByteArray
}
internal class VerifiedDeviceAuthentication(
    private val transport: VerifiedDeviceTransport,
    private val signer: DeviceSigner
) : AuthenticationApi {
    override fun register(username: String, password: CharArray) =
        transport.register(username, password)

    override fun login(username: String, password: CharArray): AuthGrant? {
        val der = try { signer.publicDer() } catch (_: Exception) { return null }
        val digest = try { DeviceLoginProof.digest(der) } catch (_: IllegalArgumentException) { return null }
        val challenge = transport.challenge(username, password, digest) ?: return null
        val signature = try {
            DeviceLoginProof.message(challenge.accountId, digest, challenge.nonce)
            signer.sign(challenge.accountId, digest, challenge.nonce)
        } catch (_: Exception) { return null }
        if (signature.size !in 1..80) return null
        val grant = transport.login(username, password, digest, challenge.nonce, der, signature)
            ?: return null
        if (grant.identity.username != username) {
            transport.revoke(grant.accessToken)
            return null
        }
        return grant
    }

    override fun revoke(accessToken: String) = transport.revoke(accessToken)
}
