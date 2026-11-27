package com.mefuturesaibot.app.ai.resource

import android.app.ActivityManager
import android.content.Context

internal data class ResourceBudget(val cachedSymbols: Int, val allowResearch: Boolean)

// Advisory budget; consumers must enforce it. Never evict active order state.
internal object SmartRamManager {
    fun budget(availableBytes: Long, thresholdBytes: Long, lowMemory: Boolean): ResourceBudget {
        if (lowMemory || availableBytes <= 0 || thresholdBytes <= 0 ||
            availableBytes <= thresholdBytes) return ResourceBudget(1,false)
        return if (availableBytes - thresholdBytes < 256L * 1024 * 1024)
            ResourceBudget(2,false) else ResourceBudget(8,true)
    }
    fun sample(context: Context): ResourceBudget {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return ResourceBudget(1,false)
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return budget(info.availMem,info.threshold,info.lowMemory)
    }
}
