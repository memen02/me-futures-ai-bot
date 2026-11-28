package com.mefuturesaibot.app.auth

import org.junit.Assert.*
import org.junit.Test

class VerifiedDeviceAuthenticationTest {
    private class Transport(var response: DeviceChallenge?) : VerifiedDeviceTransport {
        var calls = 0
        override fun register(username: String, password: CharArray) = RegistrationResult.ACCEPTED
        override fun challenge(username: String, password: CharArray, keyDigest: String) = response
        override fun login(username: String, password: CharArray, keyDigest: String,
            nonce: String, publicDer: ByteArray, signature: ByteArray): AuthGrant? {
            calls++
            return null
        }
        override fun revoke(accessToken: String) {}
    }
    private class Signer(private val fail: Boolean = false) : DeviceSigner {
        var calls = 0
        override fun publicDer() = byteArrayOf(1, 2, 3)
        override fun sign(accountId: String, digest: String, nonce: String): ByteArray {
            calls++
            check(!fail)
            return byteArrayOf(1)
        }
    }
    @Test fun deniedChallengeNeverSigns() {
        val transport = Transport(null)
        val signer = Signer()
        assertNull(VerifiedDeviceAuthentication(transport, signer).login("tester", charArrayOf('x')))
        assertEquals(0, signer.calls)
        assertEquals(0, transport.calls)
    }
    @Test fun malformedChallengeNeverSigns() {
        val transport = Transport(DeviceChallenge("a".repeat(32), "bad"))
        val signer = Signer()
        assertNull(VerifiedDeviceAuthentication(transport, signer).login("tester", charArrayOf('x')))
        assertEquals(0, signer.calls)
        assertEquals(0, transport.calls)
    }
    @Test fun signingFailureNeverSubmits() {
        val transport = Transport(DeviceChallenge("a".repeat(32), "b".repeat(64)))
        val signer = Signer(true)
        assertNull(VerifiedDeviceAuthentication(transport, signer).login("tester", charArrayOf('x')))
        assertEquals(1, signer.calls)
        assertEquals(0, transport.calls)
    }
}
