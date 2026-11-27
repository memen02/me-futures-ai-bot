package com.mefuturesaibot.app.ai.resource

import android.content.Context
import java.io.File
import java.nio.file.Files

// Only disposable research cache owned by this module. No recursive deletion.
internal class SmartStorageCleaner(context: Context) {
    private val cache = context.applicationContext.cacheDir
    @Synchronized fun clean(nowMs: Long): Int {
        require(nowMs > 0)
        val root = File(cache,"ai_disposable_research_v1")
        if (Files.isSymbolicLink(root.toPath()) || !root.isDirectory ||
            root.canonicalFile.parentFile != cache.canonicalFile) return 0
        var deleted = 0
        val entries = root.listFiles() ?: return 0
        for (file in entries.take(256)) {
            if (!file.name.matches(Regex("[a-f0-9]{64}[.]cache")) ||
                Files.isSymbolicLink(file.toPath()) || !file.isFile ||
                file.canonicalFile.parentFile != root.canonicalFile) continue
            val modified = file.lastModified()
            if (modified > 0 && nowMs >= modified && nowMs-modified >= 86400000L &&
                file.delete()) deleted++
        }
        return deleted
    }
}
