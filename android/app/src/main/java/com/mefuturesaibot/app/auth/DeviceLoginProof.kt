package com.mefuturesaibot.app.auth

import java.security.MessageDigest

object DeviceLoginProof {
    private val accountPattern = Regex("[0-9a-f]{32}")
    private val digestPattern = Regex("[0-9a-f]{64}")

    fun digest(publicDer: ByteArray): String {
        require(publicDer.size in 1..256)
        return MessageDigest.getInstance("SHA-256").digest(publicDer)
            .joinToString("") { "%02x".format(java.util.Locale.ROOT, it.toInt() and 255) }
    }

    fun message(accountId: String, keyDigest: String, nonce: String): ByteArray {
        require(accountPattern.matches(accountId))
        require(digestPattern.matches(keyDigest))
        require(digestPattern.matches(nonce))
        return "ME-PROJECT-DEVICE-LOGIN-V1:$accountId:$keyDigest:$nonce"
            .toByteArray(Charsets.US_ASCII)
    }
}
