package com.mefuturesaibot.app.maintenance

import java.nio.file.Files
import java.nio.file.attribute.FileTime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CacheCleanerTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun removesOnlyExpiredPrivateCacheAndMeasuresActualBytes() {
        val root = folder.newFolder("cache").toPath()
        val nested = Files.createDirectory(root.resolve("nested"))
        val now = System.currentTimeMillis()
        val boundary = now - CacheCleaner.retentionMillis
        val old = Files.write(nested.resolve("old"), byteArrayOf(1, 2, 3))
        Files.setLastModifiedTime(old, FileTime.fromMillis(boundary))
        val recent = Files.write(root.resolve("recent"), byteArrayOf(4))
        Files.setLastModifiedTime(recent, FileTime.fromMillis(boundary + 2000))
        val privateDir = folder.newFolder("no_backup").toPath()
        val protectedFile = Files.write(privateDir.resolve("account"), byteArrayOf(5))
        Files.setLastModifiedTime(protectedFile, FileTime.fromMillis(boundary - 5000))
        Files.createSymbolicLink(root.resolve("linked-dir"), privateDir)
        Files.createSymbolicLink(root.resolve("linked-file"), protectedFile)
        assertEquals(CleanupResult(0, 0, 0), CacheCleaner.clean(root, now) { true })
        assertTrue(Files.exists(old))
        assertEquals(CleanupResult(1, 3, 0), CacheCleaner.clean(root, now) { false })
        assertFalse(Files.exists(old))
        assertTrue(Files.exists(recent))
        assertTrue(Files.exists(protectedFile))
        assertTrue(Files.isSymbolicLink(root.resolve("linked-file")))
        assertEquals(CleanupResult(0, 0, 0), CacheCleaner.clean(root, now) { false })
    }
}
