package com.mefuturesaibot.app.maintenance

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.TimeUnit

internal data class CleanupResult(val files: Long, val bytes: Long, val skipped: Long)

internal object CacheCleaner {
    val retentionMillis = TimeUnit.DAYS.toMillis(7)

    /** The caller supplies Context.cacheDir. walkFileTree does not follow symbolic links. */
    fun clean(root: Path, now: Long, cancelled: () -> Boolean): CleanupResult {
        val cutoff = now - retentionMillis
        var count = 0L
        var bytes = 0L
        var skipped = 0L
        fun next() = if (cancelled()) FileVisitResult.TERMINATE else FileVisitResult.CONTINUE
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes) = next()

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (cancelled()) return FileVisitResult.TERMINATE
                if (attrs.isRegularFile && attrs.lastModifiedTime().toMillis() <= cutoff) {
                    try {
                        if (Files.deleteIfExists(file)) {
                            count++
                            bytes += attrs.size()
                        }
                    } catch (_: IOException) {
                        skipped++
                    }
                }
                return next()
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                skipped++
                return next()
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                if (exc != null) skipped++
                return next()
            }
        })
        return CleanupResult(count, bytes, skipped)
    }
}
