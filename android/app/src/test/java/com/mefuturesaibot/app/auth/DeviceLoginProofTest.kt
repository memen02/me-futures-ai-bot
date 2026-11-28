package com.mefuturesaibot.app.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceLoginProofTest {
    @Test fun canonicalMessage() {
        val account = "a".repeat(32)
        val digest = "b".repeat(64)
        val nonce = "c".repeat(64)
        assertEquals("ME-PROJECT-DEVICE-LOGIN-V1:$account:$digest:$nonce",
            DeviceLoginProof.message(account, digest, nonce).toString(Charsets.US_ASCII))
    }

    @Test fun knownDigest() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            DeviceLoginProof.digest("abc".toByteArray()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedNonce() {
        DeviceLoginProof.message("a".repeat(32), "b".repeat(64), "invalid")
    }
}
