package com.mefuturesaibot.app.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec

object DeviceLoginKey {
    private const val ALIAS = "me_project_backend_device_v1"

    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Synchronized
    fun publicKeyDer(): ByteArray {
        val ks = store()
        if (!ks.containsAlias(ALIAS)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build())
                generateKeyPair()
            }
        }
        return requireNotNull(store().getCertificate(ALIAS)).publicKey.encoded
    }

    @Synchronized
    fun sign(accountId: String, expectedDigest: String, nonce: String): ByteArray {
        val ks = store()
        val entry = ks.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry
            ?: error("Device key unavailable; registration required")
        require(DeviceLoginProof.digest(entry.certificate.publicKey.encoded) == expectedDigest)
        return Signature.getInstance("SHA256withECDSA").apply {
            initSign(entry.privateKey)
            update(DeviceLoginProof.message(accountId, expectedDigest, nonce))
        }.sign()
    }
}
