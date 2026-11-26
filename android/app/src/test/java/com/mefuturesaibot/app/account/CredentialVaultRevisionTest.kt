package com.mefuturesaibot.app.account

import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
class CredentialVaultRevisionTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val aes = KeyGenerator
        .getInstance("AES")
        .apply { init(256) }
        .generateKey()

    private fun open(name: String = "demo.bin") =
        CredentialVault(
            File(folder.root, name),
            { aes },
            {}
        )

    private fun credentials(
        secret: String = "test-secret"
    ) = BinanceCredentials(
        "test-key",
        secret
    )

    @Test
    fun revisionIsSharedAcrossVaultInstances() {
        val first = open()
        val second = open()

        first.save(credentials())

        val snapshot = first.readVersioned()

        assertSame(
            snapshot.revision,
            second.revision()
        )

        assertTrue(
            second.isCurrent(snapshot.revision)
        )

        assertEquals(
            "test-secret",
            snapshot.credentials.secret
        )
    }

    @Test
    fun repeatedReadsDoNotChangeRevision() {
        val vault = open()

        vault.save(credentials())

        val first = vault.readVersioned()

        repeat(20) {
            val next = vault.readVersioned()

            assertSame(
                first.revision,
                next.revision
            )

            assertTrue(
                vault.isCurrent(first.revision)
            )
        }
    }

    @Test
    fun changingOnlySecretInvalidatesOldRevision() {
        val first = open()
        val second = open()

        first.save(credentials("old-secret"))

        val old = first.readVersioned()

        second.save(credentials("new-secret"))

        val current = first.readVersioned()

        assertEquals(
            old.credentials.key,
            current.credentials.key
        )

        assertEquals(
            "new-secret",
            current.credentials.secret
        )

        assertFalse(
            first.isCurrent(old.revision)
        )

        assertTrue(
            second.isCurrent(current.revision)
        )
    }

    @Test
    fun savingIdenticalCredentialsInvalidatesRevision() {
        val vault = open()

        vault.save(credentials())

        val old = vault.revision()

        vault.save(credentials())

        assertFalse(vault.isCurrent(old))

        assertNotSame(
            old,
            vault.revision()
        )
    }

    @Test
    fun removalInvalidatesAllPreviousSnapshots() {
        val first = open()
        val second = open()

        first.save(credentials())

        val old = first.readVersioned()

        second.remove()

        assertFalse(first.exists())

        assertFalse(
            first.isCurrent(old.revision)
        )

        assertThrows(IOException::class.java) {
            first.readVersioned()
        }

        first.save(credentials("replacement"))

        assertFalse(
            second.isCurrent(old.revision)
        )

        assertEquals(
            "replacement",
            second.readVersioned().credentials.secret
        )
    }

    @Test
    fun failedSaveInvalidatesOldRevision() {
        val vault = open()

        vault.save(credentials())

        val old = vault.readVersioned()

        val broken = CredentialVault(
            File(folder.root, "demo.bin"),
            { throw IOException("Test failure") },
            {}
        )

        assertThrows(IOException::class.java) {
            broken.save(credentials("replacement"))
        }

        assertFalse(
            vault.isCurrent(old.revision)
        )

        val current = vault.readVersioned()

        assertEquals(
            "test-secret",
            current.credentials.secret
        )

        assertTrue(
            vault.isCurrent(current.revision)
        )
    }

    @Test
    fun failedKeyErasureStillInvalidatesRevision() {
        val vault = open()

        vault.save(credentials())

        val old = vault.revision()

        val broken = CredentialVault(
            File(folder.root, "demo.bin"),
            { aes },
            { throw IOException("Test failure") }
        )

        assertThrows(IOException::class.java) {
            broken.remove()
        }

        assertFalse(vault.exists())
        assertFalse(vault.isCurrent(old))
    }

    @Test
    fun demoAndMainnetRevisionsAreIndependent() {
        val demo = open("demo.bin")
        val real = open("real.bin")

        demo.save(credentials("demo-secret"))
        real.save(credentials("real-secret"))

        val demoOld = demo.readVersioned()
        val realOld = real.readVersioned()

        demo.save(credentials("new-demo-secret"))

        assertFalse(
            demo.isCurrent(demoOld.revision)
        )

        assertTrue(
            real.isCurrent(realOld.revision)
        )

        assertFalse(
            demo.isCurrent(realOld.revision)
        )

        assertEquals(
            "real-secret",
            real.readVersioned().credentials.secret
        )

        demo.remove()

        assertTrue(
            real.isCurrent(realOld.revision)
        )

        assertTrue(real.exists())
    }

    @Test
    fun equivalentPathsShareRevision() {
        val first = open()

        first.save(credentials())

        val second = CredentialVault(
            File(folder.root, "./demo.bin"),
            { aes },
            {}
        )

        assertSame(
            first.revision(),
            second.revision()
        )

        val old = first.revision()

        second.save(credentials("new-secret"))

        assertFalse(first.isCurrent(old))
    }

    @Test
    fun failedReadDoesNotChangeRevision() {
        val vault = open()

        vault.save(credentials())

        val old = vault.revision()

        val broken = CredentialVault(
            File(folder.root, "demo.bin"),
            { throw IOException("Test failure") },
            {}
        )

        assertThrows(IOException::class.java) {
            broken.readVersioned()
        }

        assertTrue(vault.isCurrent(old))
    }

    @Test
    fun concurrentReadAndReplacementStayConsistent() {
        val vault = open()

        vault.save(credentials("old-secret"))

        val old = vault.readVersioned()

        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val readerStarted = CountDownLatch(1)

        val writer = CredentialVault(
            File(folder.root, "demo.bin"),
            {
                entered.countDown()

                check(
                    release.await(
                        10,
                        TimeUnit.SECONDS
                    )
                )

                aes
            },
            {}
        )

        val executor =
            Executors.newFixedThreadPool(2)

        try {
            val write = executor.submit {
                writer.save(
                    credentials("new-secret")
                )
            }

            assertTrue(
                entered.await(
                    5,
                    TimeUnit.SECONDS
                )
            )

            val read = executor.submit<
                CredentialRevisionSnapshot
            > {
                readerStarted.countDown()
                vault.readVersioned()
            }

            assertTrue(
                readerStarted.await(
                    5,
                    TimeUnit.SECONDS
                )
            )

            assertFalse(read.isDone)

            release.countDown()

            write.get(
                5,
                TimeUnit.SECONDS
            )

            val current = read.get(
                5,
                TimeUnit.SECONDS
            )

            assertEquals(
                "new-secret",
                current.credentials.secret
            )

            assertFalse(
                vault.isCurrent(old.revision)
            )

            assertTrue(
                vault.isCurrent(current.revision)
            )
        } finally {
            release.countDown()
            executor.shutdownNow()

            assertTrue(
                executor.awaitTermination(
                    10,
                    TimeUnit.SECONDS
                )
            )
        }
    }

    @Test
    fun concurrentReplacementRejectsEveryOldRevision() {
        val vault = open()

        vault.save(credentials("initial"))

        val old = vault.revision()

        val start = CountDownLatch(1)
        val executor =
            Executors.newFixedThreadPool(4)

        try {
            val writes = (1..16).map { index ->
                executor.submit {
                    check(
                        start.await(
                            10,
                            TimeUnit.SECONDS
                        )
                    )

                    open().save(
                        credentials("secret-$index")
                    )
                }
            }

            start.countDown()

            writes.forEach {
                it.get(
                    10,
                    TimeUnit.SECONDS
                )
            }

            val current = vault.readVersioned()

            assertFalse(vault.isCurrent(old))

            assertTrue(
                vault.isCurrent(current.revision)
            )

            assertTrue(
                (1..16).any {
                    current.credentials.secret ==
                        "secret-$it"
                }
            )
        } finally {
            start.countDown()
            executor.shutdownNow()

            assertTrue(
                executor.awaitTermination(
                    10,
                    TimeUnit.SECONDS
                )
            )
        }
    }
}
