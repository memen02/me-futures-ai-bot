package com.mefuturesaibot.app.account

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class BinanceCredentials(val key: String, val secret: String)

/** A single encrypted envelope in noBackupFilesDir. Each write uses a new Keystore IV. */
internal class CredentialVault(
    location: File,
    private val loadKey: (create: Boolean) -> SecretKey,
    private val eraseKey: () -> Unit,
) {
    constructor(context: Context, demo: Boolean = false) : this(
        File(context.noBackupFilesDir, if (demo) "binance-demo.bin" else "binance-account.bin"),
        { create -> androidKey(if (demo) DEMO_ALIAS else ALIAS, create) },
        { keyStore().deleteEntry(if (demo) DEMO_ALIAS else ALIAS) })

    private val file = AtomicFile(location)
    private val identity = location.canonicalPath

    fun exists(): Boolean = synchronized(lock) {
        file.baseFile.exists() ||
            File(file.baseFile.path + ".bak").exists()
    }

    fun revision(): CredentialRevisionGuard.Revision =
        synchronized(lock) {
            revisions.current(identity)
        }

    fun isCurrent(
        expected: CredentialRevisionGuard.Revision
    ): Boolean = synchronized(lock) {
        revisions.isCurrent(identity, expected)
    }

    fun readVersioned(): CredentialRevisionSnapshot =
        synchronized(lock) {
            CredentialRevisionSnapshot(
                read(),
                revisions.current(identity)
            )
        }

    fun save(credentials: BinanceCredentials) = synchronized(lock) {
        // Invalidate before attempting any credential mutation.
        revisions.invalidate(identity)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, loadKey(true))
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { it.writeUTF(credentials.key); it.writeUTF(credentials.secret) }
        val plain = buffer.toByteArray()
        val encrypted = try { cipher.doFinal(plain) } finally { plain.fill(0) }
        val output = file.startWrite()
        try {
            output.write(1)
            output.write(cipher.iv.size)
            output.write(cipher.iv)
            output.write(encrypted)
            file.finishWrite(output)
        } catch (e: Exception) {
            file.failWrite(output)
            throw e
        }
    }

    fun read(): BinanceCredentials = synchronized(lock) {
        file.openRead().use { stream ->
            val input = DataInputStream(stream)
            require(input.readUnsignedByte() == 1 && input.readUnsignedByte() == 12)
            val iv = ByteArray(12).also(input::readFully)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, loadKey(false), GCMParameterSpec(128, iv))
            val plain = cipher.doFinal(input.readBytes())
            try {
                DataInputStream(ByteArrayInputStream(plain)).use { BinanceCredentials(it.readUTF(), it.readUTF()) }
            } finally { plain.fill(0) }
        }
    }

    fun remove() = synchronized(lock) {
        // Even partial removal must invalidate previous sessions.
        revisions.invalidate(identity)
        file.delete()
        if (exists()) throw IOException("File masih tersedia")
        eraseKey()
    }

    private companion object {
        const val ALIAS = "me_project_binance_account_v1"
        const val DEMO_ALIAS = "me_project_binance_demo_v1"
        val lock = Any()
        val revisions = CredentialRevisionGuard()
        fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        fun androidKey(alias: String, create: Boolean): SecretKey {
            val existing = keyStore().getKey(alias, null) as? SecretKey
            if (existing != null) return existing
            check(create)
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
            return generator.generateKey()
        }
    }
}
