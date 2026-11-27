package com.mefuturesaibot.app.ai.learning

internal data class StrategyVersion(val id: String, val parentId: String?, val createdAtMs: Long)

// Metadata only. No active strategy pointer or execution authority.
internal class StrategyVersionRegistry(private val capacity: Int = 64) {
    init { require(capacity in 1..256) }
    private val versions = LinkedHashMap<String, StrategyVersion>()

    @Synchronized fun register(version: StrategyVersion): Boolean {
        if (version.id.isBlank() || version.id.length > 128 || version.createdAtMs <= 0) return false
        versions[version.id]?.let { return it == version }
        if (versions.size >= capacity) return false
        if (version.parentId == null) {
            if (versions.isNotEmpty()) return false
        } else {
            val parent = versions[version.parentId] ?: return false
            if (version.createdAtMs <= parent.createdAtMs) return false
        }
        versions[version.id] = version
        return true
    }

    @Synchronized fun find(id: String): StrategyVersion? = versions[id]
}
