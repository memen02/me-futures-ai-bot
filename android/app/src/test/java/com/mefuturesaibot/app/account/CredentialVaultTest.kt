package com.mefuturesaibot.app.account

import java.io.File
import javax.crypto.KeyGenerator
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CredentialVaultTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun encryptsReplacesAndDeletesPair() {
        val file = File(folder.root, "credentials.bin")
        val aes = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        var erased = false
        fun open() = CredentialVault(file, { aes }, { erased = true })
        val vault = open()
        val sample = BinanceCredentials("example-test-key", "example-test-secret")
        vault.save(sample)
        val first = file.readBytes()
        assertFalse(first.toString(Charsets.ISO_8859_1).contains(sample.key))
        assertFalse(first.toString(Charsets.ISO_8859_1).contains(sample.secret))
        vault.save(sample)
        assertFalse(first.contentEquals(file.readBytes()))
        assertEquals(sample.secret, open().read().secret)
        vault.save(BinanceCredentials("replacement", "replacement-secret"))
        val broken = CredentialVault(file, { throw IllegalStateException() }, {})
        assertThrows(IllegalStateException::class.java) { broken.save(sample) }
        assertEquals("replacement", open().read().key)
        val damaged = file.readBytes()
        damaged[damaged.lastIndex] = (damaged.last().toInt() xor 1).toByte()
        file.writeBytes(damaged)
        assertThrows(java.security.GeneralSecurityException::class.java) { open().read() }
        vault.remove()
        assertFalse(open().exists())
        assertTrue(erased)
    }
}
